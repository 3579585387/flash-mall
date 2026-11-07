package com.flashmall.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务：商家后台接收「来单提醒」
 * <p>
 * 连接地址：ws://127.0.0.1:8080/ws/{sid}
 * <p>
 * 注意：会话容器必须是并发安全的（原版用 HashMap 是隐患）。
 * 多实例部署时，某个连接只存在于持有它的那个实例，需要借助 Redis Pub/Sub
 * 或 MQ 把消息广播到所有实例，否则只有一台机器能推送成功。
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    /** sid -> session */
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        SESSIONS.put(sid, session);
        log.info("WebSocket 连接建立: sid={}, 当前在线 {}", sid, SESSIONS.size());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到客户端 [{}] 消息: {}", sid, message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        SESSIONS.remove(sid);
        log.info("WebSocket 连接断开: sid={}, 当前在线 {}", sid, SESSIONS.size());
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
        log.warn("WebSocket 异常: sid={}", sid, error);
        SESSIONS.remove(sid);
    }

    /**
     * 群发消息（给所有在线商家端）
     */
    public void sendToAll(String message) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        SESSIONS.forEach((sid, session) -> {
            try {
                if (session.isOpen()) {
                    // BasicRemote 不是线程安全的，同一 session 并发发送需要同步
                    synchronized (session) {
                        session.getBasicRemote().sendText(message);
                    }
                }
            } catch (Exception e) {
                log.warn("推送消息失败，移除会话: sid={}", sid, e);
                SESSIONS.remove(sid);
            }
        });
    }

    /** 当前在线连接数，便于自测 */
    public int onlineCount() {
        return SESSIONS.size();
    }
}
