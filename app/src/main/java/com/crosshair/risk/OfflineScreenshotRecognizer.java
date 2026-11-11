package com.crosshair.risk;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TargetApi(30)
final class OfflineScreenshotRecognizer {
    interface Callback {
        void onPrice(String price);

        void onError(String message);
    }

    private final AccessibilityService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextRecognizer recognizer;
    private boolean closed;

    OfflineScreenshotRecognizer(AccessibilityService service) {
        this.service = service;
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    void capture(Callback callback) {
        if (closed) {
            callback.onError("离线识别服务已停止，请重新启用无障碍服务");
            return;
        }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult result) {
                            processScreenshot(result, callback);
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            postError(callback, "系统截图失败（" + errorCode
                                    + "），请移开遮挡后重试");
                        }
                    });
        } catch (RuntimeException error) {
            postError(callback, "系统未允许截图，请重新启用无障碍服务后再试");
        }
    }

    private void processScreenshot(AccessibilityService.ScreenshotResult result,
                                   Callback callback) {
        Bitmap screenshot;
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (hardware == null) {
                postError(callback, "无法读取屏幕图像");
                return;
            }
            try {
                screenshot = hardware.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
                hardware.recycle();
            }
        } catch (RuntimeException error) {
            postError(callback, "屏幕图像格式不受支持");
            return;
        } finally {
            buffer.close();
        }
        if (screenshot == null) {
            postError(callback, "无法复制屏幕图像");
            return;
        }

        try {
            int width = screenshot.getWidth();
            int height = screenshot.getHeight();
            int[] pixels = new int[width * height];
            screenshot.getPixels(pixels, 0, width, 0, 0, width, height);
            CrosshairRegionDetector.Region region = CrosshairRegionDetector.find(
                    pixels, width, height);
            if (region == null) {
                screenshot.recycle();
                postError(callback, "未找到左侧蓝色光标标签，请调出十字光标并移开浮窗");
                return;
            }
            int padding = Math.max(4, region.height() / 3);
            int left = Math.max(0, region.left - padding);
            int top = Math.max(0, region.top - padding);
            int right = Math.min(width - 1, region.right + padding);
            int bottom = Math.min(height - 1, region.bottom + padding);
            Bitmap crop = Bitmap.createBitmap(screenshot, left, top,
                    right - left + 1, bottom - top + 1);
            screenshot.recycle();
            Bitmap input = Bitmap.createScaledBitmap(crop,
                    crop.getWidth() * 3, crop.getHeight() * 3, true);
            if (input != crop) {
                crop.recycle();
            }
            recognize(input, callback);
        } catch (RuntimeException error) {
            if (!screenshot.isRecycled()) {
                screenshot.recycle();
            }
            postError(callback, "蓝色标签处理失败，请重新调出十字光标后再试");
        }
    }

    private void recognize(Bitmap input, Callback callback) {
        try {
            recognizer.process(InputImage.fromBitmap(input, 0))
                    .addOnCompleteListener(task -> {
                        input.recycle();
                        if (closed) {
                            return;
                        }
                        if (!task.isSuccessful() || task.getResult() == null) {
                            callback.onError("离线数字识别失败，请保持标签清晰后重试");
                            return;
                        }
                        String price = OfflinePriceParser.parse(task.getResult().getText());
                        if (price == null) {
                            callback.onError("蓝色标签中未识别出唯一价格，请移开浮窗后重试");
                        } else {
                            callback.onPrice(price);
                        }
                    });
        } catch (RuntimeException error) {
            input.recycle();
            postError(callback, "离线识别模型启动失败，请重新安装应用后再试");
        }
    }

    private void postError(Callback callback, String message) {
        mainHandler.post(() -> {
            if (!closed) {
                callback.onError(message);
            }
        });
    }

    void close() {
        closed = true;
        executor.shutdownNow();
        recognizer.close();
    }
}
