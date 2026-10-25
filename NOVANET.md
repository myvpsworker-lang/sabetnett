# NovaNet — راهنمای بیلد

این پروژه فورک v2rayNG است: همه‌ی قابلیت‌ها و هسته‌ی Xray دست‌نخورده، فقط ظاهر (تم Aurora، پنل اتصال، کارت‌های سرور، آیکون) و هویت برنامه عوض شده.

- نام برنامه: `NovaNet` (در `res/values/strings.xml`، متغیر `app_name`)
- applicationId: `app.novanet.client` (در `V2rayNG/app/build.gradle.kts`)

## گرفتن APK (بدون نصب چیزی روی سیستم)
1. یک مخزن جدید در GitHub بسازید و همه‌ی فایل‌های این پوشه را در آن آپلود/push کنید.
2. در مخزن: تب **Actions** → **Build NovaNet APK** → **Run workflow**.
3. بعد از پایان بیلد (حدود ۱۵ تا ۳۰ دقیقه در اجرای اول)، از پایین صفحه‌ی اجرا، آرتیفکت **NovaNet-apks** را دانلود کنید.
4. برای اکثر گوشی‌ها فایل `arm64-v8a` مناسب است؛ فایل `universal` روی همه کار می‌کند.

## امضای ثابت (برای آپدیت روی نسخه‌ی نصب‌شده)
بدون تنظیم زیر، هر بیلد با کلید موقت امضا می‌شود و برای نصب نسخه‌ی جدید باید قبلی را حذف کنید.
```
keytool -genkeypair -v -keystore novanet.jks -alias novanet -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 novanet.jks   # خروجی را در secret اول بگذارید
```
در GitHub: Settings → Secrets and variables → Actions، این چهار secret را بسازید:
`APP_KEYSTORE_BASE64`، `APP_KEYSTORE_PASSWORD`، `APP_KEYSTORE_ALIAS`، `APP_KEY_PASSWORD`.
فایل `novanet.jks` و رمزها را جای امن نگه دارید؛ گم‌شدنش یعنی آپدیت ممکن نیست.

## نکات
- لایسنس پروژه GPLv3 است؛ اگر برنامه را منتشر کنید باید سورس تغییریافته را هم با همین لایسنس منتشر کنید.
- بخش «بررسی آپدیت» و لینک‌های صفحه‌ی About هنوز به مخزن اصلی v2rayNG اشاره می‌کنند (`AppConfig.kt`)؛ در صورت نیاز به مخزن خودتان تغییر دهید.
- ورک‌فلوهای اصلی v2rayNG در `.github/upstream-workflows-disabled/` کنار گذاشته شده‌اند.
