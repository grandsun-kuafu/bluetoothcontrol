package com.grandsun.bluetoothcontrol.listener;

import com.grandsun.bluetoothcontrol.command.CommandResult;
import com.grandsun.bluetoothcontrol.exception.BleException;

public interface CommandListener {
    void onCommandSuccess();
    void onCommandFailure(BleException exception);
    void onCommandResult(CommandResult command);
}
