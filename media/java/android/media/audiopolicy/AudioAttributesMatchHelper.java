/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.audiopolicy;

import android.annotation.NonNull;
import android.media.AudioAttributes;
import android.text.TextUtils;

import java.util.Objects;

/**
 * Helper class for matching AudioAttributes.
 * @hide
 */
public class AudioAttributesMatchHelper {

    public static final int NO_MATCH = -1;

    // The following score constants are bit flags. A higher bit shift indicates a higher
    // contribution to the total match score, effectively giving it a higher attribute match.
    public static final int MATCH_ON_TAGS_SCORE = 1 << 3;
    public static final int MATCH_ON_FLAGS_SCORE = 1 << 2;
    public static final int MATCH_ON_USAGE_SCORE = 1 << 1;
    public static final int MATCH_ON_CONTENT_TYPE_SCORE = 1 << 0;
    public static final int MATCH_ON_DEFAULT_SCORE = 0;
    public static final int MATCH_ATTRIBUTES_EQUALS = MATCH_ON_TAGS_SCORE | MATCH_ON_FLAGS_SCORE
            | MATCH_ON_CONTENT_TYPE_SCORE | MATCH_ON_USAGE_SCORE;
    /** Max bit depth when computing the matching score defined by MATCH_ON_TAGS_SCORE */
    public static final int MATCH_MAX_SCORE_BIT_DEPTH = 4;

    private static final int AUDIO_FLAGS_AFFECT_STRATEGY_SELECTION =
            AudioAttributes.FLAG_AUDIBILITY_ENFORCED
                    | AudioAttributes.FLAG_SCO
                    | AudioAttributes.FLAG_BEACON;

    /**
     * @param refAttr {@link AudioAttributes} to be taken as the reference
     * @param attr {@link AudioAttributes} of the requester.
     * @return matching score
     */
    public static int attributesMatchesScore(@NonNull AudioAttributes refAttr,
            @NonNull AudioAttributes attr) {
        Objects.requireNonNull(refAttr, "Reference audio attributes must not be null");
        Objects.requireNonNull(attr, "Audio attributes to check must not be null");
        if (refAttr.equals(attr)) {
            return MATCH_ATTRIBUTES_EQUALS;
        }
        if (refAttr.equals(AudioProductStrategy.getDefaultAttributes())) {
            return MATCH_ON_DEFAULT_SCORE;
        }
        int score = 0;
        if (refAttr.getSystemUsage() == AudioAttributes.USAGE_UNKNOWN) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (attr.getSystemUsage() == refAttr.getSystemUsage()) {
            score |= MATCH_ON_USAGE_SCORE;
        } else {
            return NO_MATCH;
        }
        if (refAttr.getContentType() == AudioAttributes.CONTENT_TYPE_UNKNOWN) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (attr.getContentType() == refAttr.getContentType()) {
            score |= MATCH_ON_CONTENT_TYPE_SCORE;
        } else {
            return NO_MATCH;
        }
        String refFormattedTags = TextUtils.join(";", refAttr.getTags());
        String cliFormattedTags = TextUtils.join(";", attr.getTags());
        if (refFormattedTags.length() == 0) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (refFormattedTags.equals(cliFormattedTags)) {
            score |= MATCH_ON_TAGS_SCORE;
        } else {
            return NO_MATCH;
        }
        if ((refAttr.getAllFlags() & AUDIO_FLAGS_AFFECT_STRATEGY_SELECTION) == 0) {
            score |= MATCH_ON_DEFAULT_SCORE;
        } else if (((attr.getAllFlags() & AUDIO_FLAGS_AFFECT_STRATEGY_SELECTION) != 0)
                && ((attr.getAllFlags() & refAttr.getAllFlags()) == refAttr.getAllFlags())) {
            score |= MATCH_ON_FLAGS_SCORE;
        } else {
            return NO_MATCH;
        }
        return score;
    }
}
