package client.serverCenter.balance;

import java.util.List;

public interface LoadBalance {
    String balance(List<String> addressList);
}
