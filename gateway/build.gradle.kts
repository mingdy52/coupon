// API 관문(Spring Cloud Gateway). 본 앱과 같은 Boot 4 계열을 사용한다. 빌드: ./gradlew jibDockerBuild
plugins {
    java
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.4"
}

group = "com.mingdy"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // 본 앱은 Boot 4.1 이지만 Gateway 는 Spring Cloud 2025.1 이 맞춰진 Boot 4.0 을 쓴다.
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.7")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
    }
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    // RedisRateLimiter 는 reactive Redis 필요. 저장소는 같은 Redis 재사용.
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jib {
    from {
        // 본 앱과 같은 base 이미지 사용.
        image = "eclipse-temurin:25-jre"
        platforms {
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    to {
        image = "coupon-gateway"
        tags = setOf("latest")
    }
    container {
        // Boot 플러그인 없이 BOM 만 쓰므로 메인 클래스를 직접 지정한다.
        mainClass = "com.mingdy.gateway.GatewayApplication"
        ports = listOf("8080")
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}