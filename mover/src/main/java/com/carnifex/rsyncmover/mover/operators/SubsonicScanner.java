package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import feign.*;
import feign.codec.Decoder;
import feign.form.FormEncoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SubsonicScanner extends MoveOperator {

    private static final Logger logger = LogManager.getLogger(SubsonicScanner.class);

    private final String user;
    private final String password;
    private final String baseUrl;
    private final int millisBetweenScan;
    private volatile long lastScanTime = System.currentTimeMillis();
    private volatile boolean lock = false;
    private volatile Thread deferred = null;

    public SubsonicScanner() {
        super();
        this.user = null;
        this.password = null;
        this.baseUrl = null;
        this.millisBetweenScan = -1;
    }

    public SubsonicScanner(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.user = findArg("user", additionalArguments);
        this.password = findArg("password", additionalArguments);
        this.baseUrl = findArg("url", additionalArguments);
        this.millisBetweenScan = Integer.valueOf(findArg("minutesBetweenScan", additionalArguments, "3")) * 60 * 1000; // 3 minutes
    }

    private String findArg(final String name, final List<String> additionalArguments, final String... defaultValue) {
        final List<String> args = additionalArguments.stream()
                .filter(arg -> arg.startsWith(name))
                .map(arg -> arg.substring(name.length() + ":".length()))
                .collect(Collectors.toList());
        if (args.size() == 1) {
            return args.get(0);
        }
        if (args.isEmpty() && defaultValue.length == 1) {
            return defaultValue[0];
        }
        throw new IllegalArgumentException(name);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        scan();
        return to;
    }

    @Override
    public String getMethod() {
        return "subsonic_scanner";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return false;
    }

    public void scan() {
        if (lock) {
            logger.debug("Not scanning as already locked");
            return;
        }
        final long timeLeft = System.currentTimeMillis() - lastScanTime;
        if (timeLeft < millisBetweenScan) {
            defer(timeLeft);
            logger.debug("");
        }
        try {
            lock = true;
            login();
            doScan();
        } finally {
            lock = false;
            lastScanTime = System.currentTimeMillis();
        }
    }

    private void defer(final long timeLeft) {
        if (deferred != null || timeLeft < 0) {
            return;
        }
        deferred = new Thread(() -> {
            try {
                Thread.sleep(timeLeft);
            } catch (InterruptedException e) {
                logger.debug(e.getMessage(), e);
                return;
            }
            scan();
        });
        deferred.setDaemon(true);
        deferred.start();
    }


    private interface Subsonic {
        @RequestLine("POST /musicFolderSettings.view")
        @Headers("Content-type: application/x-www-form-urlencoded")
        Map<String, Object> scanNow(final Map<String, ?> map);

        @RequestLine("GET /login")
        @Headers("Content-type: application/x-www-form-urlencoded")
        Map<String, Object> login(@Param("j_username") final String user, @Param("j_password") final String password,
                                  @Param("submit") final String submit, @Param("remember-me") final String rememberMe,
                                  @Param("_csrf") final String csrf);

        @RequestLine("GET /")
        String root();
    }


    private void doScan() {
        // post to url + /musicFolderSettings.view?scanNow
        final Subsonic subsonic = getClient();
        final Map<String, Object> map = new HashMap<>();
        map.put("scanNow", "");
        final Object o = subsonic.scanNow(map);
    }

    private void login() {
        final String[] cookie = new String[1];
        final Subsonic subsonic = Feign.builder()
                .logLevel(feign.Logger.Level.FULL)
                .logger(new Slf4jLogger(this.getClass()))
                .client(new OkHttpClient())
                .decoder((response, type) -> {
                    if (cookie[0] == null) {
                        final String c = response.headers().get("set-cookie").iterator().next();
                        cookie[0] = c.substring(c.indexOf("JSESSIONID"), c.indexOf(";path"));
                    }
                    return new Decoder.Default().decode(response, type);
                })
                .retryer(new Retryer() {
                    @Override
                    public void continueOrPropagate(final RetryableException e) {
                        throw e;
                    }

                    @Override
                    public Retryer clone() {
                        return this;
                    }
                })
                .encoder(new FormEncoder())
                .requestInterceptor(template -> {
                    if (cookie[0] != null) {
                        template.header("Cookie", cookie[0]);
                    }
                })
                .target(Subsonic.class, this.baseUrl);
        final String root = subsonic.root();
        // should be able to find the csrf token from regex please dont hate me
        final Matcher matcher = Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})").matcher(root);
        final String csrf;
        if (matcher.find()) {
            csrf = matcher.group(1);
        } else {
            logger.debug(root);
            throw new IllegalStateException("Couldn't find csrf token!");
        }
        final Object login = subsonic.login(user, password, "Log in", "on", csrf);
    }

    private Subsonic getClient() {
        return Feign.builder()
                .encoder(new FormEncoder())
                .target(Subsonic.class, this.baseUrl);
    }
}
