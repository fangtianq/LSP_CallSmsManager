plugins {
    id("com.android.application")
}

android {
    namespace = "cn.ftqp.callsms"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.ftqp.callsms"
        minSdk = 29
        targetSdk = 34
        versionCode = 6
        versionName = "1.1.4"
    }

    buildTypes {
        release {
            // 模块靠 META-INF/xposed 元数据加载，保守起见先不开混淆；
            // 若开启，proguard-rules.pro 已备好 keep 规则。
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 开源项目惯例：release 直接用 debug 签名（AGP 自动生成的本地 debug 密钥），
            // 让 CI 出的包免配置密钥、可直接安装；要正式签名时再换成自己的 signingConfig。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // META-INF/xposed/* 必须完整进 APK，否则 LSPosed 认不出模块
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // libxposed/api 102 官方 classes.jar（取自 api-102.0.0.aar），compileOnly：
    // 运行时由 LSPosed 框架提供，绝不能打进 APK。
    // 想改用仓库依赖时把下面一行换成：
    //   compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly(files("libs/libxposed-api-102.jar"))

    implementation("androidx.appcompat:appcompat:1.6.1")
}
