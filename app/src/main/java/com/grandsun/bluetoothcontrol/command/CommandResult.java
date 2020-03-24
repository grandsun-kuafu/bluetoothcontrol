package com.grandsun.bluetoothcontrol.command;

public class CommandResult {
    private Command command;
    private String result;

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}

