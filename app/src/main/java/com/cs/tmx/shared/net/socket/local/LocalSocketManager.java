package com.cs.tmx.shared.net.socket.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cs.ide.termux.shared.jni.models.JniResult;
import com.cs.ide.termux.shared.net.socket.local.PeerCred;

public class LocalSocketManager {

    @Nullable
    public static native JniResult createServerSocketNative(@NonNull String serverTitle, @NonNull byte[] path,
            int backlog);

    @Nullable
    public static native JniResult closeSocketNative(@NonNull String serverTitle, int fd);

    @Nullable
    public static native JniResult acceptNative(@NonNull String serverTitle, int fd);

    @Nullable
    public static native JniResult readNative(@NonNull String serverTitle, int fd, @NonNull byte[] data, long deadline);

    @Nullable
    public static native JniResult sendNative(@NonNull String serverTitle, int fd, @NonNull byte[] data, long deadline);

    @Nullable
    public static native JniResult availableNative(@NonNull String serverTitle, int fd);

    public static native JniResult setSocketReadTimeoutNative(@NonNull String serverTitle, int fd, int timeout);

    @Nullable
    public static native JniResult setSocketSendTimeoutNative(@NonNull String serverTitle, int fd, int timeout);

    @Nullable
    public static native JniResult getPeerCredNative(@NonNull String serverTitle, int fd, PeerCred peerCred);

}
