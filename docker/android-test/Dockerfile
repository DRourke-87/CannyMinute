FROM budtmo/docker-android:emulator_13.0

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

RUN yes | /opt/android/cmdline-tools/tools/bin/sdkmanager --sdk_root=/opt/android --licenses \
    && /opt/android/cmdline-tools/tools/bin/sdkmanager --sdk_root=/opt/android \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;34.0.0"

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

USER androidusr
