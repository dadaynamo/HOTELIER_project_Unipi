import java.nio.channels.SocketChannel;
import java.util.Properties;
import java.util.Scanner;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.net.SocketAddress;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class ClientMain {

    public static class MulticastReceiver implements Runnable { //Thread in background per ricevere messaggi UDP al server
        //group 230.0.0.1 port 9876
        private String MULTICAST_GROUP;
        private int MULTICAST_PORT;
        private clientObject client;
        public MulticastReceiver(String group, int port, clientObject client){
             this.MULTICAST_GROUP = group;
             this.MULTICAST_PORT = port;
             this.client = client;
            // System.out.println("port "+port+ " group "+group);
         }
    
        @Override
        public void run() {
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
                socket.joinGroup(group);
    
                byte[] buffer = new byte[1024];
    
                while (true) {
                          
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        //System.out.println("Bytes ricevuti: " + Arrays.toString(packet.getData()));
                        if(client.state_login == true)
                        System.out.println("\n"+message);
                    
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    

    //OGGETTO CLIENT GEST
    public static class clientObject{
        //var private al client
        private SocketAddress address;
        private SocketChannel client;
        private Socket socket;
        private OutputStream out;
        private InputStream in;
        private int bytebuff_dim_alloc;
        //private MulticastReceiver threadReceiver;
        private Thread threadReceiver;
        //stato client
        private int port;
        private boolean state_login = false;
        private String username;
        private int portUDP;
        private String groupUDP;
        //private String password; dovrebbe non servire mai
        
        public clientObject(Properties prop){ //Costruttore
            //carico le proprietà dal file di config
            this.port = Integer.parseInt(prop.getProperty("port"));
            this.bytebuff_dim_alloc = Integer.parseInt(prop.getProperty("bytebuff_dim_alloc"));
            this.portUDP = Integer.parseInt(prop.getProperty("portUDP"));
            this.groupUDP = prop.getProperty("groupUDP");

            //setto lo stato di login a false inizialmente
            this.state_login = false;
        }
        public String toString(){ //metodo toString
            return "Client{"+
                    "address="+this.address+'\''+
                    "port="+this.port+
                    '}';        
        }

        public boolean isStringAllowed(String message){ //return true se la stringa NON HA caratteri proibiti
            return !message.contains("-");
        } 

        public void initService() throws IOException{
            try {
                this.address = new InetSocketAddress(this.port); //creo un nuovo oggetto InetSocketAddress associandogli la porta
                this.client = SocketChannel.open(this.address); //apro il canale di connessione
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            System.out.println("Client collegato con il server");
        }
        public void closeClient() throws IOException{ //chiusura client
            this.socket.close();
            this.in.close();
            this.out.close();
            System.out.println("Client chiuso FINE: OK"); //Fine       
        }
        //Metodi *********************************************************************************************************************************************************************
        
        public void registerGest() throws IOException{ //registrazione UI
            Scanner s = new Scanner(System.in);
            System.out.print("\033[H\033[2J");  
            System.out.flush();
            System.out.println("******************************");
            System.out.println("Form registrazione");
            System.out.println("******************************");
            System.out.print("Username _: ");
            String username = s.nextLine();
            System.out.print("Password _: ");
            String password = s.nextLine();
            //s.close(); //chiusura di s per memory leak : NON METTERLO ERRORE FATALE
            System.out.println("Username e password inserite sono "+username+" "+ password);
            if(isStringAllowed(username) && isStringAllowed(password)){ //se username e password non hanno caratteri proibiti continuo con la registrazione
                this.register(username,password); //funzione di registrazione
            }else{
                System.out.println("Error: Hai inserito caratteri non consentiti");
            }
        }   
        public void register(String username, String password) throws IOException{ //registrazione invio pacchetto req e ricezione res
            String code = "1";
            String message = code.concat("-").concat(username).concat("-").concat(password); // formato messaggio [code]-[val]-[val]  MESSAGGIO DA INVIARE CREATO
            System.out.println(message); 

            //gestione invio richiesta al server
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }
            //attendo risposta dal server
            ByteBuffer rispostaServer = ByteBuffer.allocate(this.bytebuff_dim_alloc); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            if(risposta.equals("100"))
            System.out.println("Notifica: registrazione ok-> "+risposta); //stampo risposta del server
            else
            System.out.println("Notifica: "+risposta); //stampo risposta del server
            
        }


        public void loginGest() throws IOException{ //login UI
            Scanner scanner = new Scanner(System.in);
            System.out.println("******************************");
            System.out.println("Form login");
            System.out.println("******************************");
            System.out.print("Username _: ");
            String username = scanner.nextLine();
            System.out.print("Password _: ");
            String password = scanner.nextLine();
           
            if(isStringAllowed(username) && isStringAllowed(password)){  //se username e password non contengono caratteri proibiti allora continuo con il login
                this.login(username,password);
            }
            //scanner.close(); //chiusura di s per memory leak  : NON METTERLO. ERRORE FATALE 
        } 
        public void login(String username, String password) throws IOException{ //login invio pacchetto req e ricezione res
            String code = "2";
            String message = code.concat("-").concat(username).concat("-").concat(password); // formato messaggio [code]-[val]-[val]  MESSAGGIO DA INVIARE CREATO
            System.out.println(message); 

            //gestione invio richiesta al server
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }

            //attendo risposta dal server
            ByteBuffer rispostaServer = ByteBuffer.allocate(this.bytebuff_dim_alloc); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            System.out.println("Notifica: "+risposta); //stampo risposta del server
            if(risposta.equals("100")){
                //login accettato
                this.state_login = true;
                this.username =  username;
                

            }else{
                //login non accettato
                this.state_login = false;
            }
        }       
    
        public void logoutGest() throws IOException {
            Scanner s = new Scanner(System.in);
            System.out.println("Clicca un tasto per fare logout");
            s.nextLine();
            logout(this.username);
            //s.close();
        }
        public void logout(String username) throws IOException{
            //capire se il logout deve essere fatto con richiesta a server o solo in locale al client
            this.state_login = false;
            this.username = "";
        }
        
        public void menuGest (){ //decido quale menu mostrare su console
        
            if(this.state_login == true){
                //utente loggato
                System.out.println("1) logout\n2) searchHotel\n3) searchAllHotels\n4) insertReview\n5) showBadeges\n0) exit");
            }else{
                //utente generico
                System.out.println("1) register\n2) login\n3) SearchHotel\n4) SearchAllHotels\n8) checkHotelList\n9) testsendmessage\n0) exit");
            }
        }
  
          
        public void testSendMessage() throws IOException{

            System.out.println("****Test send message by client to server****");
            Scanner s = new Scanner(System.in);
            String message = s.nextLine();
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }
            //attendo risposta dal server
            ByteBuffer rispostaServer = ByteBuffer.allocate(this.bytebuff_dim_alloc); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            System.out.println("Notifica: "+risposta); //stampo risposta del server
            s.close();

        }
        public void searchHotelGest() throws IOException {
            Scanner scanner = new Scanner(System.in);
            System.out.println("******************************");
            System.out.println("Cerca un Hotel");
            System.out.println("******************************");
            System.out.print("Nome Hotel _: ");
            String nomeHotel = scanner.nextLine();
            System.out.print("Città _: ");
            String città = scanner.nextLine();
           
            if(isStringAllowed(nomeHotel) && isStringAllowed(città)){  //se username e password non contengono caratteri proibiti allora continuo con il login
                this.searchHotel(nomeHotel,città);
            }
        }

        public void searchHotel(String nomeHotel, String città) throws IOException {
            String code = "4";
            String message = code.concat("-").concat(nomeHotel).concat("-").concat(città); // formato messaggio [code]-[val]-[val]  MESSAGGIO DA INVIARE CREATO
            System.out.println(message); 

            //gestione invio richiesta al server
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }
            //attendo risposta dal server
            ByteBuffer rispostaServer = ByteBuffer.allocate(this.bytebuff_dim_alloc); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            System.out.println("Notifica: "+risposta); //stampo risposta del server
            
            
        }
        public void searchAllHotelsGest() throws IOException {
            Scanner scanner = new Scanner(System.in);
            System.out.println("******************************");
            System.out.println("Cerca gli Hotel di una città in particolare");
            System.out.println("******************************");
            System.out.print("Città _: ");
            String città = scanner.nextLine();
           
            if(isStringAllowed(città)){  //se username e password non contengono caratteri proibiti allora continuo con il login
                this.searchAllHotels(città);
            }
        }
       
        public void searchAllHotels(String città) throws IOException {
            String code = "5";
            String message = code.concat("-").concat(città); // formato messaggio [code]-[val]  MESSAGGIO DA INVIARE CREATO
            System.out.println(message); 

            //gestione invio richiesta al server
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }
            //attendo risposta dal server
    
            ByteBuffer rispostaServer = ByteBuffer.allocate(10240); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            System.out.println(risposta); //stampo risposta del server
        
            
        }
        public void insertReviewGest() throws IOException {
            Scanner scanner = new Scanner(System.in);
            System.out.println("**************************************************");
            System.out.println("Inserisci una recensione a un particolare Hotel");
            System.out.println("**************************************************");
            System.out.print("Nome Hotel _: ");
            String nomeHotel = scanner.nextLine();
            System.out.print("Città _: ");
            String città = scanner.nextLine();
            System.out.print("GlobalScore _: ");
            double GlobalScore = scanner.nextDouble();
            double [] SingleScores = new double[4] ;
            System.out.print("Cleaning _: ");
            SingleScores[0] = scanner.nextInt();
            System.out.print("Position _: ");
            SingleScores[1] = scanner.nextInt();
            System.out.print("Services _: ");
            SingleScores[2] = scanner.nextInt();
            System.out.print("Quality _: ");
            SingleScores[3] = scanner.nextInt();
            
            if(isStringAllowed(nomeHotel) && isStringAllowed(città) && (SingleScores[0]>=0 || SingleScores[0]<=5 ) && (SingleScores[1]>=0 || SingleScores[1]<=5 ) && (SingleScores[2]>=0 || SingleScores[2]<=5 ) && (SingleScores[3]>=0 || SingleScores[3]<=5 )){  //se username e password non contengono caratteri proibiti allora continuo con il login
                this.insertReview(nomeHotel, città, GlobalScore, SingleScores);
            }
        }

        public void insertReview(String nomeHotel, String nomecittà, double GlobalScore, double [] SingleScores) throws IOException{
            //insert review richiesta server
            String code = "6";
            String message = code.concat("-").concat(nomeHotel).concat("-").concat(nomecittà).concat("-").concat(Double.toString(GlobalScore)).concat("-").concat(Double.toString(SingleScores[0])).concat("-").concat(Double.toString(SingleScores[1])).concat("-").concat(Double.toString(SingleScores[2])).concat("-").concat(Double.toString(SingleScores[3])).concat("-").concat(this.username); // formato messaggio [code]-[val]  MESSAGGIO DA INVIARE CREATO
            System.out.println(message); 
            
            //gestione invio richiesta al server
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }
            //attendo risposta dal server
    
            ByteBuffer rispostaServer = ByteBuffer.allocate(this.bytebuff_dim_alloc); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            System.out.println(risposta); //stampo risposta del server
        
        }

        public void showMyBadges() throws IOException {
             //insert review richiesta server
             String code = "7";
             String message = code.concat("-").concat(this.username);
             System.out.println(message); 
             
             //gestione invio richiesta al server
             ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
             buffer.put(message.getBytes());
             buffer.flip();
             while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                 this.client.write(buffer);
             }
             //attendo risposta dal server
             ByteBuffer rispostaServer = ByteBuffer.allocate(this.bytebuff_dim_alloc); //alloco un bytebuffer
             this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
             rispostaServer.flip(); //preparo il buffer alla lettura
             String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
             System.out.println(risposta); //stampo risposta del server
         
        }
      
        public void checkHotelList() throws IOException {
            //insert review richiesta server
            String code = "8";
            String message = code;
            System.out.println(message); 
            
            //gestione invio richiesta al server
            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
            buffer.put(message.getBytes());
            buffer.flip();
            while(buffer.hasRemaining()){ //invio dati sul canale finchè gli ho inviati tutti
                this.client.write(buffer);
            }
            //attendo risposta dal server
    
            ByteBuffer rispostaServer = ByteBuffer.allocate(800000); //alloco un bytebuffer
            this.client.read(rispostaServer); //attendo risposta da parte del server in modalità bloccante con la read
            rispostaServer.flip(); //preparo il buffer alla lettura
            String risposta = new String(rispostaServer.array(),0, rispostaServer.limit()); //inserisco i dati del buffer in una stringa
            System.out.println(risposta); //stampo risposta del server
        
        }

    }


    public static void main(String[] args) throws Exception { //main


        //nuovo oggetto Properties
        Properties prop = new Properties();    
        try {
            //carico il file di configurazione
            prop.load(new FileInputStream("src/config_client.dat"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //creo classe clientObject per gestione del client
        clientObject client = new clientObject(prop);
        System.out.println(client.toString());

        //client.socket = new Socket(client.address,client.port);
        MulticastReceiver threadReceiver = new MulticastReceiver(client.groupUDP, 9876, client);
        
        client.threadReceiver = new Thread(threadReceiver);
        //Attivo il thread in background per la ricezione di messaggi in UDP
        client.threadReceiver.start();

        client.initService();

              

        //MENU **********************************************************************************
        Scanner s = new Scanner(System.in);
        int scelta;
        do{
            System.out.println("******************************");
            System.out.println("Menu principale");
            client.menuGest();
            System.out.println("******************************");
            System.out.print("Inserisci scelta consentita --> ");
            
            scelta = s.nextInt();
           
/*
            
            //utente loggato
            System.out.println("1) logout\n2) searchHotel\n3) searchAllHotels\n4) insertReview\n5) showBadeges\n0) exit");
            //utente generico
            System.out.println("1) register\n2) login\n3) SearchHotel\n4) SearchAllHotels\n9) testsendmessage\n0) exit");
*/

            if(client.state_login == false){ //utente non loggato

                switch (scelta) {
                    case 1:
                        client.registerGest();
                        break;
                    case 2:                
                        client.loginGest();
                        break;
                    case 3:                
                        client.searchHotelGest();
                        break;
                    case 4:                
                        client.searchAllHotelsGest();
                        break;
                    case 9: //debug                
                        client.testSendMessage();
                        break;
                    case 8: //debug        
                        client.checkHotelList();
                        break;
                    case 0:
                        System.out.println("Uscita dal programma.");
                        break;
                    default:
                    System.out.println("Errore in Inserimento... Il client verrà chiuso");
                }

            }else{ //utente loggato
                switch (scelta) {
                    case 1:
                        client.logoutGest();
                        break;
                    case 2:
                        client.searchHotelGest();
                        break;
                    case 3:
                        client.searchAllHotelsGest();
                        break;
                    case 4:
                        client.insertReviewGest();
                        break;
                    case 5:
                        client.showMyBadges();
                        break;
                    case 0:
                        System.out.println("Uscita dal programma.");
                        break;
                    default:
                        System.out.println("Errore in Inserimento... Il client verrà chiuso");
                        scelta = 0;
                        continue;
                }
            }
        

        }while(scelta != 0);
        s.close();
        System.out.println("fine");
    }
}


  /* Funzionalità base:
        register(username, password);
        login(username, password);
        logout(username);
        searchHotel(nomeHotel, città);
        searchAllHotels(città);
        insertReview(nomeHotel, nomeCittà, GlobalScore, [] SingleScores);
        showMyBadges();
    */



