package com.grandsun.bluetoothcontrol.command;

public enum Command {

    TEMPERATURE_AND_HEARTRATE("000001", "0000180d-0000-1000-8000-00805f9b34fb",
            "00002a37-0000-1000-8000-00805f9b34fb"),

    TEMPERATURE_AND_HEARTRATE_TASK("100001", "0000180d-0000-1000-8000-00805f9b34fb",
            "00002a37-0000-1000-8000-00805f9b34fb");

    Command(String uuid, String SERVICE_UUID, String CHARACTER_UUID) {
        this.uuid = uuid;
        this.SERVICE_UUID = SERVICE_UUID;
        this.CHARACTER_UUID = CHARACTER_UUID;
    }

    private String uuid;//唯一index

    public String getUuid() {
        return uuid;
    }

    private String SERVICE_UUID;
    private String CHARACTER_UUID;

    public String getSERVICE_UUID() {
        return SERVICE_UUID;
    }


    public String getCHARACTER_UUID() {
        return CHARACTER_UUID;
    }

    public static String getCharacterUUIDByuuid(String uuid) {
        for (Command command : Command.values()) {
            if (command.getUuid().equals(uuid)) {
                return command.CHARACTER_UUID;
            }
        }
        return "";
    }

}
