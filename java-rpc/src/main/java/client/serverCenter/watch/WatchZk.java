/*
 * @Author: weihua hu
 * @Date: 2025-03-21 09:41:30
 * @LastEditTime: 2025-03-21 17:26:14
 * @LastEditors: weihua hu
 * @Description:
 */
package client.serverCenter.watch;

import client.cache.ServiceCache;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;

public class WatchZk {
    private CuratorFramework client;
    private ServiceCache cache;

    public WatchZk(CuratorFramework client, ServiceCache cache) {
        this.client = client;
        this.cache = cache;
    }

    public void watchToUpdate(String path) {
        CuratorCache curatorCache = CuratorCache.build(client, "/");
        curatorCache.listenable().addListener(
                new CuratorCacheListener() {
                    @Override
                    public void event(Type type, ChildData childData, ChildData childData1) {
                        System.out.println(type.name());
                        System.out.println("监听到变化前:");
                        if (childData != null) {
                            System.out.println(childData.getPath());
                        }
                        System.out.println("监听到变化后:");
                        if (childData1 != null) {
                            System.out.println(childData1.getPath());

                        }
                        switch (type.name()) {
                            case "NODE_CREATED":
                                String[] data = pasrePath(childData1);
                                if (data.length > 2) {
                                    String serviceName = data[1];
                                    String address = data[2];
                                    cache.addServcieToCache(serviceName, address);
                                }
                                break;
                            case "NODE_CHANGED":
                                if (childData.getData() != null) {
                                    System.out.println("修改前的数据: " + new String(childData.getData()));
                                } else {
                                    System.out.println("节点第一次赋值!");
                                }
                                String[] oldPathList = pasrePath(childData);
                                String[] newPathList = pasrePath(childData1);
                                cache.replaceServiceAddress(oldPathList[1], oldPathList[2], newPathList[2]);
                                System.out.println("修改后的数据: " + new String(childData1.getData()));
                                break;
                            case "NODE_DELETED":
                                String[] pathList_d = pasrePath(childData);
                                if (pathList_d.length <= 2)
                                    break;
                                else {
                                    String serviceName = pathList_d[1];
                                    String address = pathList_d[2];
                                    // 将新注册的服务加入到本地缓存中
                                    cache.delete(serviceName, address);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                });
        curatorCache.start();
    }

    public String[] pasrePath(ChildData childData) {
        // 获取更新的节点的路径
        String path = new String(childData.getPath());
        // 按照格式 ，读取
        return path.split("/");
    }
}
