Weaver 2.3.2 - Minecraft 26.3
====================================

[العربية في الأسفل]

Weaver is a new mod loader for Minecraft 26.3. This is a beta for mod
developers and testers; expect rough edges.

What you need
-------------
- Windows 10 or 11 (64-bit).
- Java 26 (JDK). Free download: https://adoptium.net/ or https://jdk.java.net/26/
- An internet connection for the first step (about 600 MB).
- You should own Minecraft. Minecraft's files are not in this package;
  get-game.ps1 downloads them from Mojang's own servers.

Steps
-----
1. Unzip this folder somewhere, for example to your Desktop.
2. Open the folder, click the address bar, type  powershell  and press Enter.
3. Download the game (once):
       powershell -ExecutionPolicy Bypass -File get-game.ps1
4. Play:
       powershell -ExecutionPolicy Bypass -File play.ps1 -Name YourName
   Use a name of 3-16 letters, digits, or _.

In the game, open a Creative world: the mod tabs are on page 2 of the
creative inventory (the < > arrows at the top). The "First Mod" tab has a
Ruby.

Play together
-------------
One player opens a world, presses Esc, and chooses "Open to LAN"; the game
shows a port. The other player (same network) runs:
       powershell -ExecutionPolicy Bypass -File play.ps1 -Name OtherName -Server 192.168.1.20:PORT
with the first player's IP address. Both need the same mods in .\mods, or
Weaver refuses the join and says why.

More mods
---------
Put Weaver mod jars in .\mods. Only Weaver mods work; Fabric, Forge, and
NeoForge mods do not.

If something goes wrong
-----------------------
The game runs offline (no Microsoft account), so lines like
"Failed to fetch user properties" or "Status: 401" in the log are normal.
Send the files in .\logs and say what happened. Known issues are in
docs\release-notes.md.

What is inside
--------------
weaver\agent      the Weaver loader
mods              First Mod (an example mod)
tools             the launcher scripts
docs              release notes and the developer guide
source            the full Weaver source code as one git bundle
                  (git clone source\weaver-v2.3.2.bundle weaver)
LICENSE           LGPL-2.1-only; Copyright 2026 Abdullah Haitham Al-Nuaimi
NOTICE            copyright, license, and third-party attribution
README-LICENSE.md licensing details (LGPL-2.1-only)
THIRD-PARTY.txt   licenses of the libraries Weaver uses


==================================================
Weaver 2.3.2 - نسخة بيتا للمطوّرين - Minecraft 26.3
==================================================

Weaver محمّل مودات جديد لماينكرافت 26.3. هذه نسخة تجريبية مبكرة للتجربة،
فقد تجد بعض المشاكل.

ماذا تحتاج
----------
- Windows 10 أو 11 (64 بت).
- Java 26 (JDK). تنزيل مجاني: https://adoptium.net/ أو https://jdk.java.net/26/
- اتصال إنترنت للخطوة الأولى (حوالي 600 ميغابايت).
- يجب أن تملك ماينكرافت. ملفات اللعبة ليست في هذه الحزمة؛ السكربت
  get-game.ps1 ينزّلها من خوادم Mojang الرسمية.

الخطوات
-------
1. فك ضغط المجلد في أي مكان، مثلاً على سطح المكتب.
2. افتح المجلد، اضغط على شريط العنوان، اكتب  powershell  واضغط Enter.
3. نزّل اللعبة (مرة واحدة فقط):
       powershell -ExecutionPolicy Bypass -File get-game.ps1
4. العب:
       powershell -ExecutionPolicy Bypass -File play.ps1 -Name اسمك_بالانجليزي
   الاسم من 3 إلى 16 حرفاً إنجليزياً أو أرقاماً أو _.

داخل اللعبة افتح عالماً بوضع Creative: تبويبات المودات في الصفحة 2 من
قائمة الإبداعي (الأسهم < > في الأعلى). تبويب "First Mod" فيه حجر Ruby.

اللعب مع صديق
-------------
لاعب يفتح عالماً، يضغط Esc ويختار "Open to LAN"، فتظهر اللعبة رقم منفذ.
اللاعب الآخر (على نفس الشبكة) يشغّل:
       powershell -ExecutionPolicy Bypass -File play.ps1 -Name اسم_آخر -Server 192.168.1.20:المنفذ
مع عنوان IP الخاص باللاعب الأول. يجب أن تكون نفس المودات في .\mods عند
الاثنين، وإلا يرفض Weaver الدخول ويذكر السبب.

مودات إضافية
------------
ضع ملفات مودات Weaver في المجلد .\mods. مودات Weaver فقط تعمل؛ مودات
Fabric وForge وNeoForge لا تعمل.

إذا حدثت مشكلة
--------------
اللعبة تعمل بدون حساب مايكروسوفت، لذلك أسطر مثل "Failed to fetch user
properties" أو "Status: 401" في السجل طبيعية ولا تعني مشكلة.
أرسل الملفات الموجودة في .\logs واشرح ما حدث. المشاكل المعروفة في
docs\release-notes.md.
