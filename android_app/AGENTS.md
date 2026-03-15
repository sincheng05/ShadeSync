# android_app AGENTS.md

本文件記錄 `C:\Users\user\Documents\GitHub\ShadeSync\android_app` 在 2026-03-15 這個快照下的真實開發現狀，目的是讓後續代理或開發者不要把「概念型原型」誤讀成「已接線完成的正式功能」。

如果文件與程式碼不一致，以程式碼為準；但就目前這個快照來看，下列描述與現有檔案內容一致。

## 1. 專案整體判斷

- 目前是 Android 單模組原型專案，目標是牙色比對與觀察資料記錄的開源 app。
- 專案處於 PoC / prototype 階段，不是產品完成態，也不是臨床可用狀態。
- `android_app` 內目前同時存在兩條開發脈絡：
  - 一條是 `MainActivity.kt` 內真正被啟動的相機校正/量測流程。
  - 另一條是 `feature/shadematch/*` 內較完整但尚未接上啟動流程的模組化 Compose 原型。
- 這兩條脈絡尚未整合，這是目前最重要的結構事實。

## 2. 真正會被啟動的功能

### 2.1 啟動入口

- App 的 Android 入口是 `app/src/main/AndroidManifest.xml` 宣告的 `.MainActivity`。
- `MainActivity.onCreate()` 會呼叫 `setContent { ShadeSyncTheme { ShadeSyncApp() } }`。
- 這裡實際被呼叫的是 `MainActivity.kt` 同檔案內的 `private fun ShadeSyncApp()`。
- `MainActivity.kt` 雖然 `import com.example.shadesync.feature.shadematch.ui.ShadeSyncApp`，但目前真正執行的不是那個匯入的函式，而是本檔案的私有同名函式。
- 因此，`feature/shadematch/ui/Screens.kt` 的四分頁 UI 現在不是 app 的實際啟動畫面。

### 2.2 目前主流程

實際運行中的主流程如下：

1. 啟動 app。
2. 檢查是否已有 `CAMERA` 權限。
3. 若沒有權限，顯示一顆按鈕要求使用者授權。
4. 若有權限，啟動 CameraX 預覽與影像分析。
5. 每約 250 ms 取一次畫面中央 ROI 的平均 RGB 色值。
6. 在 `Calibration` 模式中，把目前色值存到選定的 Vita 16 色階。
7. 在 `Measurement` 模式中，用目前色值對已儲存的校正值做最近鄰比對，顯示最佳色階。

### 2.3 目前主流程的技術特徵

- 色階集合是 Vita classical 16：`A1 A2 A3 A3.5 A4 B1 B2 B3 B4 C1 C2 C3 C4 D2 D3 D4`。
- 相機預覽用 CameraX 的 `PreviewView`。
- 分析器用 `ImageAnalysis`，背壓策略是 `STRATEGY_KEEP_ONLY_LATEST`。
- 目標解析度被設為 `1280 x 720`。
- ROI 不是整張圖，而是畫面中央三分之一寬乘三分之一高的區塊，也就是大約中央九分之一面積。
- ROI 不是逐像素全掃，而是每隔 8 個像素抽樣一次。
- YUV 轉 RGB 是手動做的簡化換算。
- 目前只做 RGB 平均，不做白平衡估測、曝光補正、光源標準化、鏡頭色彩校正或裝置校正矩陣。
- 實際比對演算法不是 LAB/Delta E，而是 RGB 三通道平方距離總和。
- 量測畫面只顯示單一最佳匹配，不顯示完整排序或信心區間。

### 2.4 目前資料持久化

- 只有校正資料有持久化。
- 持久化方式是 `SharedPreferences`，名稱是 `shade_calibration`。
- key 是 `VitaShade.name`，value 是 Android `Int` 型別顏色值。
- 沒有 Room、DataStore、檔案資料庫、雲端同步或後端 API。

## 3. 尚未接上啟動流程的模組化原型

`feature/shadematch/*` 這組檔案描繪的是比較完整的產品構想，但目前不是實際被 `MainActivity` 啟動的使用者流程。

### 3.1 `feature/shadematch/ui/Screens.kt`

- 定義了一個四分頁 Compose UI：`Match`、`Compare`、`Records`、`About`。
- 這個 UI 有自己的 `fun ShadeSyncApp()`，但目前沒有被 app 啟動流程使用。
- `Match` 頁面：
  - 用 RGB slider 模擬輸入顏色。
  - 可開啟 `Precision Assist`，其實作只是固定白平衡增益 `1.05 / 1.0 / 0.95`。
  - 會把 RGB 轉 LAB，再與 `Vita16Repository.shades` 計算 CIE76 Delta E。
  - 只取前三名。
- `Compare` 頁面：
  - 兩組 RGB slider 比較兩個樣本的 LAB 與 Delta E。
- `Records` 頁面：
  - 顯示去識別化資料格式說明。
  - 目前資料不是來自資料庫，而是硬編碼的 `sampleRecords()`。
- `About` 頁面：
  - 主要是產品願景、倫理原則、公共衛生假說與限制說明。

### 3.2 `feature/shadematch/data/Vita16Repository.kt`

- 宣告了 Vita 16 色卡資料庫雛形。
- 但 16 個 shade 的 `LabColor` 目前全部都是 `LabColor(0.0, 0.0, 0.0)`。
- 每個 shade 都標記：
  - `source = "VITA 16 placeholder"`
  - `version = "0.0"`
  - `isPlaceholder = true`
- 這代表這份資料目前只是佔位資料，不可拿來做真實色階比對。
- 任何基於這份 repository 的 Delta E 排名，目前都只有展示意義，沒有實際校正價值。

### 3.3 `feature/shadematch/domain/*`

- `Models.kt` 定義了比較完整的領域模型：`RgbColor`、`LabColor`、`WhiteBalanceGain`、`ShadeSwatch`、`ShadeMatch`、`RoiSample`、`SurfaceFeature`、`ObservationRecord`。
- 這些模型描述的是 app 想走向的資料結構，不等於已經完成的資料流程。
- `ObservationRecord` 已經有 shade、Delta E、surface features、device、calibration version、region、timestamp、verified、child、notes 等欄位，但目前沒有正式儲存或同步機制。

### 3.4 `feature/shadematch/domain/ColorMath.kt`

- 已有基礎色彩數學工具：套用白平衡增益、sRGB -> XYZ -> LAB、CIE76 Delta E。
- 使用的白點常數是 `D65` 常見近似值：`XN = 0.95047`、`YN = 1.0`、`ZN = 1.08883`。
- 目前只有 CIE76，沒有 CIE94、CIEDE2000。
- 這些工具主要被 `feature/shadematch/ui/Screens.kt` 的 slider 原型使用，沒有接到真實相機取樣流程。

### 3.5 `feature/shadematch/presentation/ShadeMatchState.kt`

- 定義了 `ShadeMatchState` 狀態模型。
- 目前只是一個資料類別，沒有 ViewModel、StateFlow、Reducer 或其他狀態管理接線。
- 在目前專案裡幾乎可視為未使用狀態。

## 4. 目前沒有實作的能力

以下能力在 `android_app` 內目前不存在，後續代理不要假設它們已經完成：

- 沒有真正的雲端同步或 `ShadeSync` 名稱對應的 sync 流程。
- 沒有後端 API、Retrofit、OkHttp、Ktor、Firebase。
- 沒有 Room、SQLite schema、DAO、migration。
- 沒有 DataStore。
- 沒有 WorkManager 背景工作。
- 沒有 Hilt、Dagger、Koin 或其他 DI。
- 沒有 ViewModel、LiveData、StateFlow 架構。
- 沒有 Navigation Compose。
- 沒有相機 ROI 手勢選取。
- 沒有自動曝光/白平衡標準化流程。
- 沒有設備校正檔、色彩校正矩陣或標準參考卡工作流。
- 沒有正式記錄建立流程，只有示範資料。
- 沒有匯出、匯入、分享或同步觀察紀錄。
- 沒有多語系；除了 `app_name` 外，大部分文字都直接寫死在 Kotlin 檔案裡。

## 5. 目前最重要的結構風險

### 5.1 兩套 UI 並存但未整合

- `MainActivity.kt` 內有一套真實執行的 UI。
- `feature/shadematch/ui/Screens.kt` 內有另一套模組化 UI。
- 兩者都有 `ShadeSyncApp()` 概念，但只有前者在跑。
- 這是目前最容易造成誤判的地方。

### 5.2 主流程與領域模型分裂

- 真實運行中的相機流程用的是 Android `Int` 顏色值與 `SharedPreferences`。
- 模組化原型用的是 `RgbColor`、`LabColor`、`ShadeMatch`、`ObservationRecord` 等領域模型。
- 兩套資料表示法還沒有統一。

### 5.3 正式色卡資料尚未存在

- `Vita16Repository` 目前全是 placeholder。
- 真正能動的匹配只存在於 `MainActivity.kt` 的「用使用者自行校正資料做 RGB 最近鄰」流程。
- 也就是說，目前 app 的可用匹配能力依賴使用者先手動校正，並不是內建標準牙色資料。

### 5.4 測試覆蓋仍偏薄，但已有核心邏輯保護

- UI、CameraX 綁定、權限流程與影像分析器目前仍缺少自動化測試。
- 但已補上純 Kotlin 的核心單元測試，覆蓋 `ColorMath` 與 RGB 最近鄰匹配邏輯。
- 這代表未來即使 UI 重構，色彩轉換與基本匹配行為仍可透過測試快速驗證。
## 6. 檔案層級現況總表

### 6.1 根目錄與 Gradle

- `build.gradle.kts`
  - 只有頂層 plugin alias。
  - 宣告 `com.android.application` 與 `org.jetbrains.kotlin.plugin.compose`，但不在頂層套用。
- `settings.gradle.kts`
  - 設定 pluginManagement 與 dependency repositories。
  - root project name 是 `ShadeSync`。
  - 只包含 `:app`。
  - 使用 `org.gradle.toolchains.foojay-resolver-convention`。
- `gradle.properties`
  - 仍是接近範本狀態。
  - `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`
  - `kotlin.code.style=official`
- `gradle/libs.versions.toml`
  - 管理版本號與依賴。
  - 使用 AGP `9.1.0`、Kotlin `2.2.10`、Compose BOM `2024.09.00`、CameraX `1.3.4`。
  - 也保留預設 JUnit / Espresso 版本。
- `gradle/wrapper/gradle-wrapper.properties`
  - wrapper 指向 Gradle `9.3.1`。
  - 有 SHA256 驗證值。
- `gradle/gradle-daemon-jvm.properties`
  - 生成檔。
  - 指向 Foojay 的 JDK 21 toolchain。
- `gradlew`
  - POSIX wrapper script，生成檔。
- `gradlew.bat`
  - Windows wrapper script，生成檔。

### 6.2 app module 設定

- `app/build.gradle.kts`
  - 單一 Android application module。
  - `namespace = "com.example.shadesync"`
  - `applicationId = "com.example.shadesync"`
  - `minSdk = 29`
  - `targetSdk = 36`
  - `compileSdk = 36`，並設定 `minorApiLevel = 1`
  - `versionCode = 1`
  - `versionName = "1.0"`
  - `buildFeatures.compose = true`
  - release 不混淆，`isMinifyEnabled = false`
  - 依賴包含 Compose Material3 與 CameraX。
  - 目前在 Android Studio JBR + Gradle wrapper 條件下，`testDebugUnitTest` 已可成功執行，表示這個快照下的設定可正常完成 Kotlin 編譯與 JVM 單元測試。
- `app/proguard-rules.pro`
  - 幾乎是 Android Studio 範本，未客製化。

### 6.3 Android 資源與 Manifest

- `app/src/main/AndroidManifest.xml`
  - 只有 `CAMERA` 權限。
  - 只有 `MainActivity` 一個 activity。
  - app icon 與 round icon 已設定。
  - backup/data extraction xml 已掛上。
- `app/src/main/res/values/strings.xml`
  - 只有 `app_name = ShadeSync`。
  - 其他 UI 文案沒有資源化。
- `app/src/main/res/values/colors.xml`
  - 仍是範本色票。
  - 看起來沒有對目前產品視覺進行客製。
- `app/src/main/res/values/themes.xml`
  - Android theme 是 `android:Theme.Material.Light.NoActionBar`。
  - 很精簡，仍是接近初始模板。
- `app/src/main/res/xml/backup_rules.xml`
  - 範本檔，未客製。
- `app/src/main/res/xml/data_extraction_rules.xml`
  - 範本檔，仍保留 TODO 說明。
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- `app/src/main/res/mipmap-*/ic_launcher*.webp`
  - 皆屬啟動 icon 資源。
  - 目前仍是 Android Studio 預設風格，沒有 ShadeSync 專屬品牌化。

### 6.4 目前真正使用中的 Kotlin 檔

- `app/src/main/java/com/example/shadesync/MainActivity.kt`
  - 目前最核心、最重要的檔案。
  - 同時包含 activity 入口、app mode enum、Vita shade enum、calibration store、主 UI、camera preview、image analyzer、calibration panel、measurement panel、RGB -> `RgbDistanceMatcher` 轉接、色值 hex 格式化。
  - 這表示目前功能高度集中在單一檔案，還沒有完成分層或拆模組。
  - 相機綁定例外被 `catch (_: Exception) {}` 吃掉，沒有任何錯誤回報 UI。
  - 權限流程只有請求，不處理拒絕後的說明或設定導引。

### 6.5 feature/shadematch 相關 Kotlin 檔

- `app/src/main/java/com/example/shadesync/feature/shadematch/data/Vita16Repository.kt`
  - Vita 16 placeholder repository。
- `app/src/main/java/com/example/shadesync/feature/shadematch/domain/Models.kt`
  - 領域模型定義。
- `app/src/main/java/com/example/shadesync/feature/shadematch/domain/ColorMath.kt`
  - 色彩數學工具。
- `app/src/main/java/com/example/shadesync/feature/shadematch/domain/RgbDistanceMatcher.kt`
  - 純 Kotlin 的 RGB 最近鄰匹配工具。
  - 已被 `MainActivity.kt` 與單元測試使用。
- `app/src/main/java/com/example/shadesync/feature/shadematch/presentation/ShadeMatchState.kt`
  - 狀態模型，未形成正式 presentation layer。
- `app/src/main/java/com/example/shadesync/feature/shadematch/ui/Components.kt`
  - 模組化原型 UI 元件，如 `SectionCard`、`RgbSliders`、`ColorSwatch`。
- `app/src/main/java/com/example/shadesync/feature/shadematch/ui/Screens.kt`
  - 模組化四分頁原型。
  - 目前不是 app 啟動路徑。

### 6.6 主題相關 Kotlin 檔

- `app/src/main/java/com/example/shadesync/ui/theme/Color.kt`
  - 仍是 Compose 範本紫色系。
- `app/src/main/java/com/example/shadesync/ui/theme/Theme.kt`
  - 使用 Material3。
  - Android 12+ 會優先使用 dynamic color。
  - 否則退回範本紫色系。
- `app/src/main/java/com/example/shadesync/ui/theme/Type.kt`
  - 幾乎是範本 Typography。

### 6.7 測試檔

- `app/src/test/java/com/example/shadesync/ExampleUnitTest.kt`
  - 仍保留預設加法測試。
- `app/src/test/java/com/example/shadesync/feature/shadematch/domain/ColorMathTest.kt`
  - 驗證白平衡夾值、黑白色 LAB 轉換與 Delta E 基本性質。
- `app/src/test/java/com/example/shadesync/feature/shadematch/domain/RgbDistanceMatcherTest.kt`
  - 驗證 RGB 平方距離與最近鄰匹配結果。
- `app/src/androidTest/java/com/example/shadesync/ExampleInstrumentedTest.kt`
  - 仍是確認 package name 的預設測試。
## 7. 目前 UI / 功能層面的真實成熟度

- 已有可展示的相機預覽與即時色值更新。
- 已有 Vita 16 手動校正儲存。
- 已有基於已校正樣本的單一最佳匹配。
- 已有 LAB/Delta E 原型數學與 UI，但沒有接到實機相機流程。
- 已有觀察紀錄資料模型，但只有 demo card，沒有真實資料生命週期。
- 已有產品願景與倫理界線文字，但還沒有對應的落地資料治理或部署機制。

簡單說：

- 「相機取樣 + 手動校正 + RGB 最近鄰比對」是目前最接近可運作的部分。
- 「標準化牙色資料庫 + LAB/Delta E + 去識別紀錄 + 公共衛生分析」目前仍主要是概念原型與資料模型。

## 8. 對後續代理最重要的工作原則

- 不要假設 `feature/shadematch/ui/Screens.kt` 已經上線；它現在沒有被真正啟動。
- 若要改行為，先決定是：
  - 繼續沿用 `MainActivity.kt` 的相機原型並逐步重構，或
  - 把 `feature/shadematch/*` 正式接成主流程。
- 在這個決策完成前，不要只改其中一套 UI 就宣稱功能完成。
- 不要把 `Vita16Repository` 的 placeholder 當成真實校正資料。
- 不要把目前 app 敘述成診斷工具；從程式碼與文案看，它目前只適合做訊號/假說/比對原型。

## 9. 本次驗證狀態

- 本文件仍以 `android_app` 內現有 Gradle、Kotlin、XML 與資源檔逐一閱讀為基礎。
- 另外已在此工作環境中找到 Android Studio JBR，並以專案內 `GRADLE_USER_HOME` 執行 `.\gradlew.bat testDebugUnitTest`。
- 該命令已成功完成，代表目前快照下至少可通過 Kotlin 編譯與 JVM 單元測試。
- 尚未在此回合執行的是裝置端 instrumented test 與實機相機流程驗證。

