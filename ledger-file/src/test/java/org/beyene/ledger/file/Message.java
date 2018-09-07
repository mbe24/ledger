package org.beyene.ledger.file;

import javax.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"command", "request"})
@XmlRootElement
public class Message {

    @XmlElement
    private Command command;

    @XmlElement
    private String request;


    public Command getCommand() {
        return command;
    }

    public Message setCommand(Command command) {
        this.command = command;
        return this;
    }

    public String getRequest() {
        return request;
    }

    public Message setRequest(String request) {
        this.request = request;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;

        Message message = (Message) o;

        if (command != message.command) return false;
        return request.equals(message.request);

    }

    @Override
    public int hashCode() {
        int result = command.hashCode();
        result = 31 * result + request.hashCode();
        return result;
    }
}

enum Command {
    APPROVE, DISAPPROVE;
}
