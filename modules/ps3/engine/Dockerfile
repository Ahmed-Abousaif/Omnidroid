FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive ANDROID_HOME=/sdk
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential git ca-certificates curl unzip python3 ninja-build pkg-config \
    nasm yasm autoconf automake libtool ccache glslang-tools openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
# The SDK command-line Java libraries are supplied read-only by the local build
# runner; sdkmanager selects Linux packages from the JVM's host platform.
WORKDIR /work
