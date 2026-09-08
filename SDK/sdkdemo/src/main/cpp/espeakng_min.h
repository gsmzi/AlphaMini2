#pragma once

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    AUDIO_OUTPUT_PLAYBACK = 0,
    AUDIO_OUTPUT_RETRIEVAL = 1,
    AUDIO_OUTPUT_SYNCHRONOUS = 2
} espeak_AUDIO_OUTPUT;

typedef struct {
    int type;
    int unique_identifier;
    int position;
    int length;
    int audio_position;
    int sample;
    int text_position;
    int text_length;
    int sound_icon;
    int reserved1;
    int reserved2;
    int reserved3;
    int reserved4;
    int reserved5;
} espeak_EVENT;

typedef int (*espeak_CALLBACK)(short *wav, int numsamples, espeak_EVENT *events);

int espeak_Initialize(espeak_AUDIO_OUTPUT output, int buflength, const char *path, int options);
int espeak_SetVoiceByName(const char *name);
void espeak_SetSynthCallback(espeak_CALLBACK cb);
int espeak_Synth(const void *text, size_t size, unsigned int position, int position_type,
                 unsigned int end_position, unsigned int flags, unsigned int *unique_identifier,
                 void *user_data);
int espeak_Synchronize(void);

#ifdef __cplusplus
}
#endif
