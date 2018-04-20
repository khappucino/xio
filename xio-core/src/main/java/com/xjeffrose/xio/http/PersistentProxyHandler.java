package com.xjeffrose.xio.http;

import com.google.common.hash.Funnels;
import com.xjeffrose.xio.client.ClientConfig;
import com.xjeffrose.xio.core.Constants;
import com.xjeffrose.xio.core.SocketAddressHelper;
import com.xjeffrose.xio.server.RendezvousHash;
import io.netty.util.internal.PlatformDependent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.val;

public class PersistentProxyHandler extends ProxyHandler {
  private final RendezvousHash<CharSequence> persistentProxyHasher;
  private final Map<String, ClientConfig> clientConfigMap = PlatformDependent.newConcurrentHashMap();

  public PersistentProxyHandler(
    ClientFactory factory, ProxyRouteConfig config, SocketAddressHelper addressHelper) {
    super(factory, config, addressHelper);
    val clientConfigs = config.clientConfigs();
    persistentProxyHasher =
      buildHasher(clientConfigMap, clientConfigs.size());
  }

  private RendezvousHash<CharSequence> buildHasher(
    Map<String, ClientConfig> configMap, int configSize) {
    List<String> randomIdPool = new ArrayList<>();
    val clientConfigs = config.clientConfigs();

    // map each client config to a randomly-generated ID
    for (int i = 0; i < configSize; i++) {
      String id = UUID.randomUUID().toString();
      randomIdPool.add(id);
      configMap.put(id, clientConfigs.get(i));
    }

    return new RendezvousHash<>(Funnels.stringFunnel(Constants.DEFAULT_CHARSET), randomIdPool);
  }

  @Override
  public ClientConfig getClientConfig(Request request) {
    String rawXFF = request.headers().get(X_FORWARDED_FOR).toString();
    String remoteAddress = rawXFF;
    if (rawXFF.contains(",")) {
      // extract originating address
      remoteAddress = rawXFF.substring(0, rawXFF.indexOf(","));
    } else if (rawXFF == null) {
      remoteAddress = request.host();
    }
    val hasherPoolId = persistentProxyHasher.getOne(remoteAddress.getBytes(Constants.DEFAULT_CHARSET));
    return clientConfigMap.get(hasherPoolId);
  }
}
