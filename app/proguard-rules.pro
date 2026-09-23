# R8 rules for Hertz.
#
# app/build.gradle.kts referenced this file long before it existed, so the release
# config had never actually been exercised. Rules here are deliberately narrow: the
# TarsosDSP 2.5 jar was checked for reflection (Class.forName, getDeclaredMethod,
# newInstance) and service loaders and uses none, so it needs no blanket keep.

# The hand-written Android microphone bridge lives inside the TarsosDSP package
# namespace and implements TarsosDSPAudioInputStream. AudioDispatcher calls close()
# through that interface -- it is the only place AudioRecord is released, so losing it
# would leak the microphone against other apps.
-keep class be.tarsos.dsp.io.android.** { *; }

# The AudioRecord-backed stream is constructed reflectively by nothing, but its
# interface methods are invoked polymorphically by the dispatcher.
-keep interface be.tarsos.dsp.io.TarsosDSPAudioInputStream { *; }

# R8 unboxes this enum and removes the class, which selects the pitch detector inside
# PitchProcessor's constructor. Enum unboxing is a sound optimization and Yin survives
# either way, but this is the one code path that cannot be verified without a real
# microphone, so it is kept rather than assumed correct. Costs roughly one class.
-keep enum be.tarsos.dsp.pitch.PitchProcessor$PitchEstimationAlgorithm { *; }

# TarsosDSP ships a module-info.class, which R8 does not need and cannot use.
-dontwarn module-info

# slf4j-nop is a no-op backend; its absence of bindings is intentional.
-dontwarn org.slf4j.**

# Keep line numbers so release stack traces stay readable, but hide the original
# source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
