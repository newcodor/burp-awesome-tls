package burp;

import com.google.gson.Gson;

import java.io.PrintWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class BurpExtender implements IBurpExtender, IHttpListener, IExtensionStateListener {
    private PrintWriter stdout;
    private PrintWriter stderr;
    private Gson gson;
    private Settings settings;

    private IExtensionHelpers helpers;
    private IBurpExtenderCallbacks callbacks;

    private static final String HEADER_KEY = "Awesometlsconfig";

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
    {
        this.stdout = new PrintWriter(callbacks.getStdout(), true);
        this.stderr = new PrintWriter(callbacks.getStderr(), true);
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.gson = new Gson();
        this.settings = new Settings(callbacks);

        callbacks.setExtensionName("Awesome TLS");
        callbacks.registerHttpListener(this);
        callbacks.registerExtensionStateListener(this);
        callbacks.addSuiteTab(new SettingsTab(this.settings));

        new Thread(() -> {
            String err = ServerLibrary.INSTANCE.StartServer(this.settings.getAddress());
            if (!err.equals("")) {
                boolean isGraceful = err.contains("Server stopped"); // server was stopped gracefully by calling StopServer()
                PrintWriter out = isGraceful ? this.stdout : this.stderr;
                out.println(err);
                if (!isGraceful) callbacks.unloadExtension(); // fatal error; disable the extension
            }
        }).start();
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        if (!messageIsRequest) return;

        IHttpService httpService = messageInfo.getHttpService();
        IRequestInfo req = this.helpers.analyzeRequest(messageInfo.getRequest());

        TransportConfig transportConfig = new TransportConfig();
        transportConfig.Host = httpService.getHost();
        transportConfig.Scheme = httpService.getProtocol();
        transportConfig.Fingerprint = this.settings.getFingerprint();
        transportConfig.HexClientHello = this.settings.getHexClientHello();
        transportConfig.HttpTimeout = this.settings.getHttpTimeout();
        transportConfig.HttpKeepAliveInterval = this.settings.getHttpKeepAliveInterval();
        transportConfig.IdleConnTimeout = this.settings.getIdleConnTimeout();
        transportConfig.TlsHandshakeTimeout = this.settings.getTlsHandshakeTimeout();
        String goConfigJSON = this.gson.toJson(transportConfig);
        this.stdout.println("Using config: " + goConfigJSON);

        List<String> headers = req.getHeaders();
        headers.add(HEADER_KEY + ": " + goConfigJSON);

        try {
            URL url = new URL("https://" + this.settings.getAddress());
            messageInfo.setHttpService(helpers.buildHttpService(url.getHost(), url.getPort(), url.getProtocol()));
            messageInfo.setRequest(helpers.buildHttpMessage(headers, Arrays.copyOfRange(messageInfo.getRequest(), req.getBodyOffset(), messageInfo.getRequest().length)));
        } catch (Exception e) {
            this.stderr.println("Failed to intercept http service: " + e);
            this.callbacks.unloadExtension();
        }
    }

    @Override
    public void extensionUnloaded() {
        String err = ServerLibrary.INSTANCE.StopServer();
        if (!err.equals("")) {
            this.stderr.println(err);
        }
    }
}
