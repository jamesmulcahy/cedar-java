/*
 * Copyright Cedar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cedarpolicy.model.exception;

import com.cedarpolicy.CedarJson;
import com.cedarpolicy.model.DetailedError;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when Cedar policy text fails to parse, carrying the structured diagnostics Cedar
 * produced for each error.
 *
 * <p>Cedar reports parse failures as {@code miette} diagnostics: a message, the source span
 * of the offending token, the tokens the parser expected there, and often help text. Prior
 * to this type those were flattened to a single {@code Display} string, so callers saw
 * "unexpected token `::`" with no indication of where in the policy it occurred, and every
 * error after the first was discarded. {@link #getDetailedErrors()} returns the full set,
 * one {@link DetailedError} per parse error, in the order Cedar reported them.
 *
 * <p>Extends {@link InternalException} so existing {@code catch} blocks are unaffected.
 * Two message details differ from the generic error path, deliberately: the
 * {@code "Internal JNI Error: "} prefix is dropped, because it describes the binding rather
 * than the policy and reads as a library fault rather than a typo in the caller's input;
 * and {@link #getErrors()} carries one entry per parse error rather than a single entry for
 * the whole document, which is what its plural contract always implied.
 */
public class PolicyParseException extends InternalException {

    private static final TypeReference<List<DetailedError>> ERROR_LIST =
            new TypeReference<List<DetailedError>>() {};

    private final transient List<DetailedError> detailedErrors;

    /**
     * Construct from the JSON array of {@code DetailedError} the native layer serialises.
     *
     * @param messages one message per parse error, for {@link #getErrors()}
     * @param detailedErrorsJson JSON array of {@code DetailedError}; if it cannot be read,
     *     the exception still carries {@code messages} and {@link #getDetailedErrors()}
     *     returns empty, so a serialisation change can never turn a parse error into a
     *     different failure
     */
    public PolicyParseException(String[] messages, String detailedErrorsJson) {
        super(messages);
        this.detailedErrors = readDetailedErrors(detailedErrorsJson);
    }

    private static List<DetailedError> readDetailedErrors(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            List<DetailedError> parsed = CedarJson.objectReader().forType(ERROR_LIST).readValue(json);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * The structured diagnostics for each parse error, including source spans and help text.
     *
     * @return the diagnostics, or an empty list if none could be recovered
     */
    public List<DetailedError> getDetailedErrors() {
        return Collections.unmodifiableList(detailedErrors);
    }
}
