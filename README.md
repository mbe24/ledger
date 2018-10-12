ledger
======

Overview
---------

This library providedes a ledger abstraction and hides the underlying DLT or
non-DLT solution.

Usage
-----

### Gradle ####

```Gradle
repositories {
    maven { url 'https://dl.bintray.com/mbe24/ledger' }
    maven { url 'https://jitpack.io' }
}

dependencies {
    compile 'org.beyene.ledger:ledger-iota:1.0.3'
}
```

### Maven #####

```xml
<repositories>
    <repository>
        <id>bintray-mbe24-ledger</id>
        <name>bintray</name>
        <url>https://dl.bintray.com/mbe24/ledger</url>
    </repository>

    <repository>
        <id>jitpack.io</id>
        <name>jitpack</name>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>org.beyene.ledger</groupId>
    <artifactId>ledger-iota</artifactId>
    <version>1.0.3</version>
</dependency>
```