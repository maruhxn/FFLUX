plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.github.openfeign.querydsl:querydsl-jpa:7.7")
    ksp("io.github.openfeign.querydsl:querydsl-ksp-codegen:7.7")
}
