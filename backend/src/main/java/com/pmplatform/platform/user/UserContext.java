package com.pmplatform.platform.user;

import java.util.UUID;

/**
 * Who is making the current request, as far as the request itself says: the JWT
 * subject in production, or the dev "Viewing as" user id. Resolved into a
 * {@link User} by {@link CurrentUserService}. Set and cleared by the tenant filter.
 */
public final class UserContext {

    private record Identity(String jwtSubject, String verifiedEmail, UUID devUserId) {
    }

    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    /** Set once this request has tried to link the sign-in, so it is not retried on every lookup. */
    private static final ThreadLocal<Boolean> LINK_TRIED = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(String jwtSubject, UUID devUserId) {
        set(jwtSubject, null, devUserId);
    }

    /**
     * @param verifiedEmail the token's {@code email}, only when the identity provider marks it
     *        verified ({@code email_verified}); used to link a first sign-in to the person an
     *        administrator added
     */
    public static void set(String jwtSubject, String verifiedEmail, UUID devUserId) {
        CURRENT.set(new Identity(jwtSubject, verifiedEmail, devUserId));
        LINK_TRIED.remove();
    }

    /** True the first time it is called for the current identity, false afterwards. */
    static boolean firstLinkAttempt() {
        if (Boolean.TRUE.equals(LINK_TRIED.get())) {
            return false;
        }
        LINK_TRIED.set(Boolean.TRUE);
        return true;
    }

    static String verifiedEmail() {
        Identity i = CURRENT.get();
        return i == null ? null : i.verifiedEmail();
    }

    static String jwtSubject() {
        Identity i = CURRENT.get();
        return i == null ? null : i.jwtSubject();
    }

    static UUID devUserId() {
        Identity i = CURRENT.get();
        return i == null ? null : i.devUserId();
    }

    public static void clear() {
        CURRENT.remove();
        LINK_TRIED.remove();
    }
}
