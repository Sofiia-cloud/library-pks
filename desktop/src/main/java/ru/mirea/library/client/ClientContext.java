package ru.mirea.library.client;

public final class ClientContext {
    private static final ApiClient API =
            new ApiClient(
                    System.getProperty(
                            "library.api.url",
                            System.getenv()
                                    .getOrDefault("LIBRARY_API_URL", "http://localhost:8080")));

    private ClientContext() {}

    public static ApiClient api() {
        return API;
    }
}
