====================================================
Privacy-X - ডাউনলোড এবং ব্যবহারের নির্দেশিকা (Bangla + English)
====================================================

১. ফোল্ডার পরিচিতি:
- Privacy-X-Downloads/Android/PrivacyX-debug.apk
  -> এটি আপনার আগে থেকেই বিল্ড করা আসল Android Debug APK (~73 MB)।
- Privacy-X-Downloads/Web/
  -> বর্তমানে Privacy-X শুধুমাত্র Android (Kotlin + Jetpack Compose) অ্যাপ হিসেবে তৈরি। প্রোজেক্টে কোনো Web ভার্সন তৈরি করা নেই। তাই কোনো ভুয়া বা অসম্পূর্ণ ওয়েব ফাইল এখানে রাখা হয়নি।

২. Android ফোনে APK ইন্সটল করার নিয়ম:
ধাপ ১: ফাইল এক্সপ্লোরার থেকে Privacy-X-Downloads/Android/PrivacyX-debug.apk ফাইলটি আপনার কম্পিউটার বা ফোনে ডাউনলোড করুন।
ধাপ ২: ফোনে ফাইলটি ওপেন করে "Install from Unknown Sources" (অজানা উৎস থেকে ইন্সটল) অনুমতি দিন এবং Install চাপুন।
ধাপ ৩: Google দিয়ে Sign In করুন এবং একটি ইউনিক @username বেছে নিন।

৩. গুরুত্বপূর্ণ তথ্য (Supabase ও 7-Day Auto-Delete):
- .env ফাইলে SUPABASE_URL এবং SUPABASE_ANON_KEY সেট করা আছে, কিন্তু SUPABASE_MEDIA_BRIDGE_URL এখনো সেট করা নেই।
- ছবি/ভয়েস নোট নিরাপদভাবে Supabase-এ আপলোড ও 7 দিন পর অটো-ডিলিট চালু করতে হলে Supabase-এ SQL migration (supabase/migrations/20261116_chat_media.sql) ও Edge Function (supabase/functions/media-bridge এবং cleanup-expired-media) ডিপ্লয় করে SUPABASE_MEDIA_BRIDGE_URL AI Studio Secrets-এ যোগ করতে হবে।
- ভিডিও মেসেজিং ও স্টোরি সম্পূর্ণভাবে বন্ধ রাখা হয়েছে (শুধু লাইভ ভিডিও কল করা যাবে)।
