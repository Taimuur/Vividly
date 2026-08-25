# Consumers of vividly-camera need no extra ProGuard/R8 rules.
#
# CameraX and ML Kit ship their own consumer rules, and this module keeps no
# reflection-based entry points of its own: everything crossing the boundary is
# ordinary Kotlin code that R8 can trace from the public API.
#
# This file is intentionally empty apart from these notes, and exists so the rule
# set is an explicit, reviewable part of the artifact rather than an omission.
