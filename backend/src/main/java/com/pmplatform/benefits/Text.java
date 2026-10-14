package com.pmplatform.benefits;

import com.pmplatform.platform.web.BusinessRuleException;

/** Trimming and length checks for free text. */
final class Text {

    private Text() {
    }

    static String optional(String s, int max, String what) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new BusinessRuleException(what + " is at most " + max + " characters");
        }
        return t;
    }

    static String required(String s, int max, String what) {
        String t = optional(s, max, what);
        if (t == null) {
            throw new BusinessRuleException(what + " is required");
        }
        return t;
    }
}
