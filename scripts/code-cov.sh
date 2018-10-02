#!/bin/bash -x
./gradlew -q -PtestCoverageEnabled=true -no-daemon build jacocoTestReport assembleAndroidTest
echo no | android create avd --force -n test -t android-28 --abi armeabi-v7a
emulator -avd test -no-skin -no-audio -no-window &
android-wait-for-emulator
adb shell setprop dalvik.vm.dexopt-flags v=n,o=v
./gradlew connectedCheck
bash <(curl -s https://codecov.io/bash)
