package com.grandsun.bluetoothcontrol.command;

public enum Command {

    TEMPERATURE_AND_HEARTRATE("0000180d-0000-1000-8000-00805f9b34fb",
            "00002a37-0000-1000-8000-00805f9b34fb");

    Command(String SERVICE_UUID, String CHARACTER_UUID) {
        this.SERVICE_UUID = SERVICE_UUID;
        this.CHARACTER_UUID = CHARACTER_UUID;
    }


    private String SERVICE_UUID;
    private String CHARACTER_UUID;

    public String getSERVICE_UUID() {
        return SERVICE_UUID;
    }

    public void setSERVICE_UUID(String SERVICE_UUID) {
        this.SERVICE_UUID = SERVICE_UUID;
    }

    public String getCHARACTER_UUID() {
        return CHARACTER_UUID;
    }

    public void setCHARACTER_UUID(String CHARACTER_UUID) {
        this.CHARACTER_UUID = CHARACTER_UUID;
    }
}
