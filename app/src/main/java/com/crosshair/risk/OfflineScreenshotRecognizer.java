package com.crosshair.risk;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@TargetApi(30)
final class OfflineScreenshotRecognizer {
    interface Callback {
        void onResult(String price, String name, String code, String headerText,
                      String errorMessage);
    }

    private final AccessibilityService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextRecognizer recognizer;
    private final TextRecognizer chineseRecognizer;
    private boolean closed;

    OfflineScreenshotRecognizer(AccessibilityService service) {
        this.service = service;
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        chineseRecognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
    }

    void capture(Callback callback) {
        if (closed) {
            callback.onResult(null, null, null, null,
                    "离线识别服务已停止，请重新启用无障碍服务");
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
                            postResult(callback, null, null, null, null,
                                    "系统截图失败（" + errorCode + "），请移开遮挡后重试");
                        }
                    });
        } catch (RuntimeException error) {
            postResult(callback, null, null, null, null,
                    "系统未允许截图，请重新启用无障碍服务后再试");
        }
    }

    private void processScreenshot(AccessibilityService.ScreenshotResult result,
                                   Callback callback) {
        Bitmap screenshot;
        HardwareBuffer buffer = result.getHardwareBuffer();
        try {
            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (hardware == null) {
                postResult(callback, null, null, null, null, "无法读取屏幕图像");
                return;
            }
            try {
                screenshot = hardware.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
                hardware.recycle();
            }
        } catch (RuntimeException error) {
            postResult(callback, null, null, null, null, "屏幕图像格式不受支持");
            return;
        } finally {
            buffer.close();
        }
        if (screenshot == null) {
            postResult(callback, null, null, null, null, "无法复制屏幕图像");
            return;
        }

        String price = null;
        String priceError = null;
        String name = null;
        String code = null;
        String headerText = null;
        try {
            int width = screenshot.getWidth();
            int height = screenshot.getHeight();
            int[] pixels = new int[width * height];
            screenshot.getPixels(pixels, 0, width, 0, 0, width, height);

            CrosshairRegionDetector.Region region = CrosshairRegionDetector.find(
                    pixels, width, height);
            if (region == null) {
                priceError = "未找到左侧蓝色光标标签，请调出十字光标并移开浮窗";
            } else {
                int padding = Math.max(4, region.height() / 3);
                int left = Math.max(0, region.left - padding);
                int top = Math.max(0, region.top - padding);
                int right = Math.min(width - 1, region.right + padding);
                int bottom = Math.min(height - 1, region.bottom + padding);
                Bitmap crop = Bitmap.createBitmap(screenshot, left, top,
                        right - left + 1, bottom - top + 1);
                Text text = read(recognizer, scaled(crop, 3), 10);
                if (text == null) {
                    priceError = "离线数字识别失败，请保持标签清晰后重试";
                } else {
                    price = OfflinePriceParser.parse(text.getText());
                    if (price == null) {
                        priceError = "蓝色标签中未识别出唯一价格，请移开浮窗后重试";
                    }
                }
            }

            int headerLeft = Math.round(width * 0.04f);
            int headerTop = Math.round(height * 0.012f);
            int headerRight = Math.round(width * 0.96f);
            int headerBottom = Math.round(height * 0.2f);
            if (headerRight - headerLeft > 40 && headerBottom - headerTop > 20) {
                Bitmap header = Bitmap.createBitmap(screenshot, headerLeft, headerTop,
                        headerRight - headerLeft, headerBottom - headerTop);
                Text headerResult = read(chineseRecognizer, scaled(header, 2), 20);
                if (headerResult != null) {
                    headerText = headerResult.getText();
                    StockInfoEngine.Result byPosition = StockInfoEngine.fromOcrLines(
                            ocrLines(headerResult, headerLeft, headerTop, 2), width, height);
                    StockInfoEngine.Result byText = StockInfoEngine.fromText(headerText);
                    name = byPosition.hasName() ? byPosition.name : byText.name;
                    code = byPosition.hasCode() ? byPosition.code : byText.code;
                }
            }
        } catch (RuntimeException error) {
            priceError = priceError == null
                    ? "截图处理失败，请重新调出十字光标后再试" : priceError;
        } finally {
            screenshot.recycle();
        }
        postResult(callback, price, name, code, headerText, priceError);
    }

    private static List<StockInfoEngine.OcrLine> ocrLines(Text text, int offsetLeft,
                                                          int offsetTop, int scale) {
        List<StockInfoEngine.OcrLine> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String value = line.getText();
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                Rect box = line.getBoundingBox();
                if (box == null) {
                    lines.add(new StockInfoEngine.OcrLine(value,
                            offsetLeft, offsetTop, offsetLeft, offsetTop));
                } else {
                    lines.add(new StockInfoEngine.OcrLine(value,
                            offsetLeft + box.left / scale,
                            offsetTop + box.top / scale,
                            offsetLeft + box.right / scale,
                            offsetTop + box.bottom / scale));
                }
            }
        }
        return lines;
    }

    private static Bitmap scaled(Bitmap bitmap, int factor) {
        try {
            Bitmap result = Bitmap.createScaledBitmap(bitmap,
                    bitmap.getWidth() * factor, bitmap.getHeight() * factor, true);
            if (result != bitmap) {
                bitmap.recycle();
            }
            return result;
        } catch (RuntimeException error) {
            return bitmap;
        }
    }

    private Text read(TextRecognizer target, Bitmap input, int seconds) {
        try {
            return Tasks.await(target.process(InputImage.fromBitmap(input, 0)),
                    seconds, TimeUnit.SECONDS);
        } catch (Exception error) {
            return null;
        } finally {
            input.recycle();
        }
    }

    private void postResult(Callback callback, String price, String name, String code,
                            String headerText, String errorMessage) {
        mainHandler.post(() -> {
            if (!closed) {
                callback.onResult(price, name, code, headerText, errorMessage);
            }
        });
    }

    void close() {
        closed = true;
        executor.shutdownNow();
        recognizer.close();
        chineseRecognizer.close();
    }
}
