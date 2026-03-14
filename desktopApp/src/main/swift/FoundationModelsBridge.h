#ifndef FLARE_FOUNDATION_MODELS_BRIDGE_H
#define FLARE_FOUNDATION_MODELS_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t flare_foundation_models_is_supported(void);
int32_t flare_foundation_models_is_available(
    int32_t *errorCode,
    char **errorMessage
);
char *flare_foundation_models_generate(
    const char *prompt,
    int32_t *errorCode,
    char **errorMessage
);
void flare_foundation_models_free_string(char *value);

#ifdef __cplusplus
}
#endif

#endif
