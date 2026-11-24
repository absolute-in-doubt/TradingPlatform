package absolute_in_doubt.trading_platform.getaway.security;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal requireUser(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new RuntimeException("Authentication required: missing or invalid Bearer token");
        }
        return currentUser;
    }

    public static UserPrincipal requireRole(UserPrincipal currentUser, String role) {
        requireUser(currentUser);
        if (!role.equalsIgnoreCase(currentUser.getRole())) {
            throw new RuntimeException("Access denied: required role " + role + " but user has " + currentUser.getRole());
        }
        return currentUser;
    }
}
