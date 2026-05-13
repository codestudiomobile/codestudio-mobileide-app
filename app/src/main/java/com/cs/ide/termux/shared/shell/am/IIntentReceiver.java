package com.cs.ide.termux.shared.shell.am;

import android.content.Intent;
import android.os.Bundle;
import android.os.IInterface;

public interface IIntentReceiver extends IInterface {
    abstract class Stub implements IIntentReceiver {
        public static IIntentReceiver asInterface(android.os.IBinder binder) {
            return null;
        }

        public abstract void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered,
                boolean sticky, int sendingUser);
    }
}
