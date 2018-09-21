package org.beyene.ledger.file;

import org.beyene.ledger.api.Data;
import org.beyene.ledger.api.Ledger;
import org.beyene.ledger.api.Mapper;
import org.beyene.ledger.api.Transaction;
import org.junit.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;

public class FileLedgerStringTest {

    private Ledger<Message, String> ledger;
    private Path txs;

    private AtomicInteger folderIndex = new AtomicInteger(0);

    @Before
    public void setUp() throws Exception {
        txs = Paths.get(System.getProperty("user.dir"), "txs-" + folderIndex.getAndIncrement());
        Files.createDirectories(txs);

        JAXBContext context = JAXBContext.newInstance(Message.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        Mapper<Message, String> mapper = new MessageMapper(marshaller, unmarshaller);
        ledger = new FileLedger<>(mapper, Data.STRING, txs);
    }

    @After
    public void tearDown() throws Exception {
        Files.walk(txs)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void testGetAndGetSingle() throws Exception {
        Message message = new Message().setCommand(Command.APPROVE).setRequest("APP007");
        ledger.addTransaction(message);

        List<Message> messages = ledger.getTransactions(Instant.MIN, Instant.MAX)
                .stream()
                .map(Transaction::getObject)
                .collect(Collectors.toList());

        Assert.assertThat(messages.size(), is(1));
        Assert.assertThat(messages, hasItems(message));
    }

    @Test
    public void testGetAndGetMulti() throws Exception {
        Message messageApprove = new Message().setCommand(Command.APPROVE).setRequest("APP007");
        ledger.addTransaction(messageApprove);
        Message messageDisapprove = new Message().setCommand(Command.DISAPPROVE).setRequest("DAP007");
        ledger.addTransaction(messageDisapprove);

        List<Message> messages = ledger.getTransactions(Instant.MIN, Instant.MAX)
                .stream()
                .map(Transaction::getObject)
                .collect(Collectors.toList());

        Assert.assertThat(messages.size(), is(2));
        Assert.assertThat(messages, hasItems(messageApprove));
        Assert.assertThat(messages, hasItems(messageDisapprove));
    }

    private static class MessageMapper implements Mapper<Message, String> {

        private final Marshaller marshaller;
        private final Unmarshaller unmarshaller;

        public MessageMapper(Marshaller marshaller, Unmarshaller unmarshaller) {
            this.marshaller = marshaller;
            this.unmarshaller = unmarshaller;
        }

        @Override
        public Message deserialize(String s) {
            StringReader reader = new StringReader(s);
            Message result = null;
            try {
                result = unmarshaller.unmarshal(new StreamSource(reader), Message.class).getValue();
            } catch (JAXBException e) {
                throw new IllegalStateException(e);
            }
            return result;
        }

        @Override
        public String serialize(Message message) {
            StringWriter sw = new StringWriter();
            try {
                marshaller.marshal(message, sw);
            } catch (JAXBException e) {
                throw new IllegalStateException(e);
            }

            return sw.toString();
        }
    }
}