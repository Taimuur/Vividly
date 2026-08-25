# Consumers of vividly-compose need no extra ProGuard/R8 rules.
#
# Compose ships its own consumer rules, and this module exposes only ordinary
# composables and data classes that R8 can trace from the public API.
