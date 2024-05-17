import java.lang.reflect.Type;
import java.io.*;
import com.google.gson.Gson;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/*
CODE MESSAGE:
    100 OK
    101 ERROR
*/

public class ServerMain {

    public static class MulticastSender implements Runnable { //thread background Sender UDP
        private String multicastGroup;
        private int multicastPort;
        private DatagramSocket socket;
        private Server server;

        public MulticastSender(String group, int port, Server server) {
            this.multicastGroup = group;
            this.multicastPort = port;
            this.server = server;
            try {
                this.socket = new DatagramSocket();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            try {
                //Calcolo i LocalRank di ogni hotel
                server.updateLocalRanking();

                //Scrivo sul json la HotelMap
                server.convertHotelListToJson(server.convertHotelMapToList(server.hotelMap));

                //Mi salvo in una concurrent hash map i primi di ogni città
                ConcurrentHashMap<String, Hotel> firstHotelMap = new ConcurrentHashMap<>();
                for (String city : server.hotelMap.keySet()) {
                    List<Hotel> hotels = server.hotelMap.get(city);
                    firstHotelMap.put(city, hotels.get(0));
                }

                //Eseguo la funzione di ranking e riordino tutte le liste delle città secondo i LocalRanking degli hotel
                for(String city : server.hotelMap.keySet()){
                    List<Hotel> hotels = server.hotelMap.get(city);
                    //ordiniamo la lista
                    List<Hotel> sortedHotels = hotels.stream()
                        .sorted(Comparator.comparingDouble(Hotel::getLocalRanking))
                        .collect(Collectors.toList());
                    server.hotelMap.put(city,sortedHotels);
                }


                //controllo che per ogni città riordinata ho un diverso 
                String finalmessage = "";
                for (String city : server.hotelMap.keySet()) {
                    List<Hotel> hotels = server.hotelMap.get(city);
                    Hotel newTopHotel = hotels.get(0);
                    
                    Hotel oldTopHotel = firstHotelMap.get(city);
                    //System.out.println(newTopHotel.getName()+ " "+oldTopHotel.getName());
                    //System.out.println(!oldTopHotel.getName().equals(newTopHotel.getName()));
                    if (!oldTopHotel.getName().equals(newTopHotel.getName())) {
                        finalmessage = finalmessage.concat("città: ").concat(newTopHotel.getCity()).concat(" - ").concat(newTopHotel.getName()).concat(" ; ");
                    }
                }


                //System.out.println(finalmessage);
                if(!finalmessage.equals("")){
                    //Invio messaggio finale al Multicast group
                    InetAddress group = InetAddress.getByName(multicastGroup);
                    //String message = "Messaggio finale";
                    byte[] buffer = finalmessage.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, multicastPort);
                    socket.send(packet);
                }
                System.out.println("Messaggio UDP inviato al gruppo multicast: " + finalmessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
  
    }

    public static class User{
        //json to lista utenti storati
        private String username;
        private String password;
        private String badge;
        private int numReview;
        public User(String username, String password, String badge){
            this.username = username;
            this.password = password;
            this.badge = badge;
            this.numReview = 0;
        }
        public String getUsername(){
            return this.username;
        }
    }

    public static class Hotel{
        private int id;
        private String name;
        private String description;
        private String city;
        private String phone;
        private List<String> services;
        private double rate;
        private Ratings ratings;
        private Double LocalRanking; //punteggio associato al singolo hotel usato per il riordinamento nella lista: simulo il ranking degli hotel
        private int numReview; //numero review associate a un particolare hotel
        // private int numRate;
        // private int numCleaning;
        // private int numPosition;
        // private int numServices;
        // private int numQuality;

        public void upgradeNumReview(){
            this.numReview++;
        }
        public double getLocalRanking() {
            return LocalRanking;
        }
        public void setLocalRanking(double localRanking) {
            this.LocalRanking = localRanking;
        }
        public Hotel(int id, String name, String description, String city, String phone, List<String> services, int rate, Ratings ratings ){
            this.id = id;
            this.name = name;
            this.description = description;
            this.city = city;
            this.phone = phone;
            this.services = services;
            this.rate = rate;
            this.ratings = ratings;
        }
        public int getId() {
            return id;
        }
        public String getName() {
            return name;
        }
        public String getDescription() {
            return description;
        }
        public String getCity() {
            return city;
        }
        public String getPhone() {
            return phone;
        }
        public double getRate() {
            return rate;
        }
        public List<String> getServices() {
            return services;
        }
        public Ratings getRatings() {
            return ratings;
        }

        public void setRate(double val){
            this.rate = val;
        }

        @Override
        public String toString() {
            return "ID: " + this.id + "\n" +
                   "Nome: " + this.name + "\n" +
                   "Descrizione: " + description + "\n" +
                   "Città: " + this.city + "\n" +
                   "Telefono: " + this.phone + "\n" +
                   "Servizi: " + String.join(", ", services) + "\n"+
                   "Rate: " + this.rate + "\n" +
                   "Ratings "+ this.ratings.cleaning + " ; "+ this.ratings.position + " ; "+ this.ratings.services + " ; "+ this.ratings.quality;
        }
        public String toString(int i) {
            return "Nome: " + this.name + "\n" +
                   "Rate: " + this.rate + "\n" +
                   "Ratings "+ this.ratings.cleaning + " ; "+ this.ratings.position + " ; "+ this.ratings.services + " ; "+ this.ratings.quality;
        }
    }
    
    public static class Ratings{
        private double cleaning;
        private double position;
        private double services;
        private double quality;
        public Ratings(double cleaning, double position, double services, double quality){
            this.cleaning = cleaning;
            this.position = position;
            this.services = services;
            this.quality = quality;
        }
        public double getCleaning() {
            return cleaning;
        }
        public double getPosition() {
            return position;
        }
        public double getQuality() {
            return quality;
        }
        public double getServices() {
            return services;
        }
        public void setCleaning(double val) {
            this.cleaning = val;
        }
        public void setPosition(double val) {
            this.position = val;
        }
        public void setQuality(double val) {
        this.quality = val;
        }
        public void setServices(double val) {
            this.services = val;
        }
    }

    public static class Review{

        private String nameHotel;
        private String username;
        private String city;
        private double rate;
        private Ratings ratings;
        private String date;
        public Review(String nomeHotel,String username, String città, double rate, Ratings ratings, String date){
            this.nameHotel = nomeHotel; this.username = username; this.city = città; this.rate = rate; this.ratings = ratings; this.date = date; 
        }
        public String getCity() {
            return this.city;
        }
        public String getNameHotel() {
            return this.nameHotel;
        }
        public String getUsername() {
            return this.username;
        }
        public double getRate() {
            return this.rate;
        }
        public Ratings getRatings() {
            return this.ratings;
        }
        public String getDate(){
            return this.date;
        }
    
    }
    
    public static class Server{
        //var di stato
        private String address; //localhost
        private int port; //1234
        private String pathHotels; //path json hotel
        private String pathUsers; //path json utenti registrati
        private String pathReviews; //path json Recensioni fatte
        private int bytebuff_dim_alloc; //dimensione predefinita per i buffer
        private int period_scheduled;
        private String groupUDP;
        private int portUDP;
        //private int num_hotels_buffered; //numero di un chunk di hotel prelevati nel searchAllHotels : -> DEPRECATO

        //var per socket server
        private ServerSocketChannel serverChannel; 
        private ServerSocket ss;
        private Selector selector;
        private boolean serviceStatus;
        private ConcurrentHashMap<String, List<Hotel>> hotelMap; //lista hotel
        private ConcurrentHashMap<String, List<Review>> reviewMap; //lista recensioni 
        //penso 
        /*
         Lista utenti
         Lista Hotels <array[20] di hashmap sulle città come chiave>
         */


        //*****************************************************************************************************************************************
        //GESTIONE SERVER SERVIZIO 
        //*****************************************************************************************************************************************
        public Server(Properties prop){ //costruttore di un server
            //carico le proprietà dal file di config
            this.address = prop.getProperty("address");   //è localhost
            this.port = Integer.parseInt(prop.getProperty("port")); //1234
            this.pathHotels = prop.getProperty("path_hotel"); //path del file degli hotel
            this.pathUsers = prop.getProperty("path_users"); //path del file degli utenti registrati
            this.pathReviews = prop.getProperty("path_reviews"); //path del file delle Reviews create
            this.period_scheduled = Integer.parseInt(prop.getProperty("period_scheduled")); //path del file delle Reviews create
            //this.num_hotels_buffered = Integer.parseInt(prop.getProperty("num_hotels_buffered")); //numero di un chunk di hotel prelevati nel searchAllHotels
            this.bytebuff_dim_alloc = Integer.parseInt(prop.getProperty("bytebuff_dim_alloc")); //dimensione bytebuffer al momento dell'allocazione
            this.groupUDP = prop.getProperty("groupUDP"); //
            this.portUDP = Integer.parseInt(prop.getProperty("portUDP")); //
            this.serviceStatus = false; //stato del servizio di connesso
            
        }
       
        public void initService(){ //Inizializzazione del servizio server
            try {
                this.serverChannel = ServerSocketChannel.open(); //apertura canale socket
                this.ss = serverChannel.socket(); //pongo in ss il socket associato al canale
                this.ss.bind(new InetSocketAddress(this.port)); //assegnazione di una porta di connessione sul socket
                serverChannel.configureBlocking(false); //configurazione richieste non bloccanti
                this.selector = Selector.open(); //apertura selector
                this.serverChannel.register(selector, SelectionKey.OP_ACCEPT); //registro al canale il selettore appena creato

            } catch (Exception e) { //gest error se la init non va a buon fine
                e.printStackTrace();
                return;
            }
            this.serviceStatus = true; //service attivo
            System.out.println("Server ora in ascolto sulla porta 1234 per futuri client"); //server in ascolto
        }
        
        public void closeService(){ //Chiusura del servizio server

        }

        public void serviceGest(){ //Gestione Tramite NIO delle richieste in ingresso e in uscita dal socket
            while(true){
                //Provo a fare select sul selettore
                try {selector.select();} catch (Exception e) {e.printStackTrace(); return;}
                //carico in readyKeys la lista delle chiavi prelevate in quel momento dal selettore
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                //creo l'iteratore sulle chiavi
                Iterator<SelectionKey> iterator = readyKeys.iterator();
    
                //ciclo una chiave alla volta con l'iteratore e gestisco ogni tipo di evento
                while(iterator.hasNext()){
                    //prelevo chiave dal set
                    SelectionKey key = iterator.next();
                    iterator.remove(); //rimuove la chiave solo nella selected set, ma non dal registered set
    
                    try {
                        //connessioni nuove
                        if(key.isAcceptable()){
                            ServerSocketChannel server = (ServerSocketChannel)key.channel();
                            SocketChannel client = server.accept();
                            System.out.println("Accettato connessione da "+client);
                            client.configureBlocking(false);
                            SelectionKey key2 = client.register(selector, SelectionKey.OP_READ);
                            ByteBuffer output = ByteBuffer.allocate(4);
                            output.putInt(0);
                            output.flip();
                            key2.attach(output); //inserisco come attach il dato da inviare successivamente
                           
                        }else if(key.isWritable()){  //gestione scrittura
                            SocketChannel client = (SocketChannel)key.channel();
                            ByteBuffer buffer = (ByteBuffer) key.attachment();
                            client.write(buffer);
                            if (!buffer.hasRemaining()) {
                                key.interestOps(SelectionKey.OP_READ);
                            }


                        }else if(key.isReadable()){
                            // System.out.println("client ha inviato un messaggio");
                            SocketChannel client = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(this.bytebuff_dim_alloc);
                            int bytesRead = client.read(buffer);
                            //System.out.println("bytes letti "+bytesRead);
                           
                            if(bytesRead == -1){  //il client ha chiuso la connessione
                                client.close();
                                key.cancel();
                            }else{ //lettura messaggio arrivato dal client
                                buffer.flip();
                                byte[] bytes = new byte[buffer.remaining()];
                                buffer.get(bytes);
                                String message = new String(bytes);
                                System.out.println("il client mi ha inviato "+message);
                                
                                //Analisi messaggio                       
                                //Splitto il messaggio per capire cosa deve fare il server
                                String[] text_parts = message.split("-");
                                //int numparts = text_parts.length;
                                switch (text_parts[0]) {
                                    case "1": //registrazione
                                        if(this.register(text_parts[1],text_parts[2])){
                                            //invio di una risposta al client positiva
                                            String risposta = "100";
                                            ByteBuffer bufferRisposta = ByteBuffer.wrap(risposta.getBytes());
                                            client.register(selector,SelectionKey.OP_WRITE, bufferRisposta);
                                        }else{
                                            //invio di una risposta al client positiva
                                            String risposta = "Error Register";
                                            ByteBuffer bufferRisposta = ByteBuffer.wrap(risposta.getBytes());
                                            client.register(selector,SelectionKey.OP_WRITE, bufferRisposta); 
                                        }
                                        break;
                                    case "2": //login
                                        if(this.login(text_parts[1],text_parts[2])){
                                            //invio di una risposta al client positiva
                                            String risposta = "100";
                                            ByteBuffer bufferRisposta = ByteBuffer.wrap(risposta.getBytes());
                                            client.register(selector,SelectionKey.OP_WRITE, bufferRisposta);
                                        }else{
                                            //invio di una risposta al client positiva
                                            String risposta = "Error Register";
                                            ByteBuffer bufferRisposta = ByteBuffer.wrap(risposta.getBytes());
                                            client.register(selector,SelectionKey.OP_WRITE, bufferRisposta); 
                                        }
                                        break;
                                    case "4": //searchHotel
                                        String risposta = this.searchHotel(text_parts[1],text_parts[2]);
                                        ByteBuffer bufferRisposta = ByteBuffer.wrap(risposta.getBytes());
                                        client.register(selector,SelectionKey.OP_WRITE, bufferRisposta); 
                                        break;
                                    case "5": //searchAllHotels
                                        String response = this.searchAllHotels(text_parts[1],client);
                                        System.out.println("Notifica: "+response);
                                        break;
                                    case "6": //insertReview
                                        //provo la funzione per la creazione in memoria permanente una nuova recensione

                                        double [] singleScores = {Double.parseDouble(text_parts[4]),Double.parseDouble(text_parts[5]),Double.parseDouble(text_parts[6]),Double.parseDouble(text_parts[7])};
                                        
                                        String risposta1 =  this.insertReview(text_parts[1],text_parts[2],Double.parseDouble(text_parts[3]),singleScores,text_parts[8]);
                                        //Aggiorno i contatori e i rating di ogni hotel
                                        this.updateRatings();
                                        //aggiorno il numero di recensioni fatte da un utente
                                        this.updateCountReviewUser(text_parts[8]);
                                        //Aggiorno il numero di recensioni collegate a un hotel
                                        this.updateCountReviewHotel(text_parts[1],text_parts[2]);

                                        ByteBuffer bufferRisposta1 = ByteBuffer.wrap(risposta1.getBytes());
                                        client.register(selector,SelectionKey.OP_WRITE, bufferRisposta1); 
                                        //se è andata a buon fine aggiorno il calcolo delle caratteristiche dell'hotel
                                      
                                        break;
                                    case "7": //showMyBadges
                                        String badge = takeBadgeofUser(text_parts[1]);  
                                        ByteBuffer bufferRisposta3 = ByteBuffer.wrap(badge.getBytes());
                                        client.register(selector,SelectionKey.OP_WRITE, bufferRisposta3); 
                                        break;
                                        case "8": //debug: HotelMap
                                        String risposta2 =  this.stringifyHotelMap();
                                        //Aggiorno i contatori e i rating di ogni hotel
                                        this.updateRatings();
                                        ByteBuffer bufferRisposta2 = ByteBuffer.wrap(risposta2.getBytes());
                                        client.register(selector,SelectionKey.OP_WRITE, bufferRisposta2); 
                                        break;
                                    default:
                                        System.out.println("ricevuto messaggio ingestibile");
                                        break;
                                }
                            }
                        }   
                    } catch (Exception e) {
                        key.cancel();
                        try {
                            key.channel().close();
                        } catch (Exception ex) {}
                    }
                }
            }    
        }



        //*****************************************************************************************************************************************
        //GESTIONE SERVER THREAD ESTERNO METODI 
        //***************************************************************************************************************************************** 

        public void updateLocalRanking() throws IOException{
            //Test stampa lista hotel da cuncurrenthashmap
            for(String city : this.hotelMap.keySet()){
                //System.out.println("Citta -> "+city);
                List<Hotel> hotelsInCity = this.hotelMap.get(city);
                for(Hotel hotel : hotelsInCity){
                    //per ogni città modifico il localranking 
                    //Calcolo della data della recensione più recente
                    Long date =this.takeDateofReviews(hotel,city);
                    //System.out.println("Recensione recente " +date.intValue());
                    //System.out.println("Formula fatta: "+ "1/ ("+hotel.rate+"*0.5+ ("+hotel.ratings.cleaning +"+"+ hotel.ratings.position +"+"+ hotel.ratings.quality +"+"+ hotel.ratings.services+")*0*2+"+date.intValue()+"*0.1)");
                    if(hotel.rate == 0 || hotel.ratings.cleaning ==0 || hotel.ratings.position ==0 || hotel.ratings.quality ==0 || hotel.ratings.services ==0){hotel.LocalRanking =1000.0;}
                    else
                    hotel.LocalRanking = 1/(hotel.rate * 0.5 + (hotel.ratings.cleaning + hotel.ratings.position + hotel.ratings.quality + hotel.ratings.services)*0.2 + hotel.numReview * 0.2 + date.intValue()*0.1); 
                    //System.out.println(hotel.name+" "+hotel.LocalRanking);
                }
            }

        }

        public Long takeDateofReviews(Hotel hoteltake,String città){ 
            for(String city : this.hotelMap.keySet()){ //Scorro tutte le città
                if(city.equals(città)){ //se la città è uguale alla città in cui voglio cercare controllo la lista degli hotel in quella città
                    List<Hotel> hotelsInCity = this.hotelMap.get(city);
                    for(Hotel hotel : hotelsInCity){
                        if(hotel.equals(hoteltake)){
                            //prelevo la recensione più recente
                            try {
                                List<Review> reviews = readReviewsListFromJson(this.pathReviews);    
                                if(reviews.isEmpty()){return (long) 1;}
                                List<Review> reviewsHotel = getHotelReviews(hotel,reviews);
                                if(reviewsHotel.isEmpty()){return (long) 1;}
                                Long min = Long.MAX_VALUE;
                                //prendo la review più recente
                                for(Review rev : reviewsHotel){
                                              
                                    String dateString = rev.date;
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                    try {
                                        // Parsa la stringa di data nel formato "yyyy-MM-dd" in un oggetto Date
                                        Date date = dateFormat.parse(dateString);
                                        //System.out.println("Data convertita: " + date);
                                        //controllo se questa data è la più recente
                                        Date now = new Date();
                                        long differenzaInMillisecondi = now.getTime() - date.getTime();

                                        // Converti la differenza da millisecondi a giorni
                                        long differenzaInGiorni = differenzaInMillisecondi / (1000 * 60 * 60 * 24);
                                        
                                        if(differenzaInGiorni < min) min = differenzaInGiorni;

                                    } catch (ParseException e) {
                                        // Gestione dell'errore nel caso in cui la stringa di data non sia nel formato corretto
                                        e.printStackTrace();
                                    }
                                  
                                }
                                return min;
                            } catch (Exception e) {
                                return (long) 0;
                            }
                            
                            
                        }
                    }
                } 
            }
            return (long) 0;

    
          
        }







        //*****************************************************************************************************************************************
        //GESTIONE RECENSIONI
        //*****************************************************************************************************************************************
        
        public String insertReview(String nomeHotel, String nomecittà, double GlobalScore, double [] SingleScores, String username) throws IOException{ //Inserimento condizionato di una review
            //creazione nuova review con queste proprietà nel file json

            //creazione nuovo oggetto Review
            Date datenow = new Date();
            // Formattare la data come stringa nel formato YYYY-MM-DD
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            String dataFormattata = formatter.format(datenow);
            
            Ratings newRatings = new Ratings(SingleScores[0],SingleScores[1],SingleScores[2],SingleScores[3]);
            Review newReview = new Review(nomeHotel, username, nomecittà, GlobalScore, newRatings,dataFormattata);

            //controllare che la review sia effettivamente collegato a un hotel esistente
            //controllo se la città esiste. se esiste controllo se ci sta quel hotel con quel nome
            // Verifica se la città della nuova recensione esiste già come chiave nella ConcurrentHashMap hotelMap
            
            if (!hotelMap.containsKey(nomecittà)) {
                System.out.println("La città specificata non esiste nella mappa degli hotel.");
                return "Errore non trovata la citta";
            }

            // Recupera la lista degli hotel della stessa città inserita nella recensione
            List<Hotel> cityHotel = this.hotelMap.get(nomecittà);
            if (cityHotel == null || cityHotel.isEmpty()) {
                System.out.println("Nessuna recensione trovata per la città specificata.");
                return "Errore non trovati Hotel in quella città";
            }
            //da vedere se serve questa stampa
            // Crea una lista di nomi degli hotel della città
            List<String> hotelNames = cityHotel.stream()
                                                .map(Hotel::getName)
                                                .collect(Collectors.toList());

          
            // Verifica se vi è un hotel con lo stesso nome nella lista
            if (!hotelNames.contains(nomeHotel)) {
                System.out.println("L'hotel specificato non esiste nella città specificata.");
                return "Errore non trovato Hotel con quel nome";
            }
            

            //Scrittura su file json la nuova recensione
            if(addReviewToJson(newReview)){
                //aggiorno la lista delle review tenuta dal server organizzato in hashmap
                List<Review> reviewList = this.readReviewsListFromJson(this.pathReviews);
                //Organizza le recensioni per città in una ConcurrentHashMap
                this.reviewMap = this.groupReviewsByCity(reviewList);
                return "Review inserita";
            }  else return "Review non creata per problemi di scrittura";
        }

        public boolean addReviewToJson(Review newReview) throws IOException { //Scrittura di una nuova recensione nel JSON 
            //System.out.println(this.pathReviews);
            List<Review> reviews = readReviewsListFromJson(this.pathReviews);
            reviews.add(newReview);
            try (FileWriter writer = new FileWriter(this.pathReviews)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(reviews, writer);
                System.out.println("Review aggiunta in JSON");
            }catch(Exception e ){
                e.printStackTrace();
                return false;
            }
            return true;
        }

        public List<Review> readReviewsListFromJson(String filePath) throws IOException { //Ricavo la lista generale delle review direttamente dal json
            try (FileReader reader = new FileReader(filePath)) {
                Type type = new TypeToken<List<Review>>(){}.getType();
                Gson gson = new Gson();
                return gson.fromJson(reader, type);
            }catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }

        public ConcurrentHashMap<String, List<Review>> groupReviewsByCity(List<Review> reviews) { //Data una lista di reviews organizzo in HashMap 
            ConcurrentHashMap<String, List<Review>> reviewsByCity = new ConcurrentHashMap<>();
            for (Review review : reviews) {
                String city = review.getCity();
                if (!reviewsByCity.containsKey(city)) {
                    reviewsByCity.put(city, new ArrayList<>());
                }
                reviewsByCity.get(city).add(review);
            }
            return reviewsByCity;
        }
        
        public void writeReviewListToJson(List<Review> reviewList) throws IOException{ //scrivo una lista di utenti nel json
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            File file = new File(this.pathReviews);
    
            // Scrivi la lista degli utenti nel file JSON
            Writer writer = new FileWriter(file);
            gson.toJson(reviewList, writer);
            writer.close();    
        }



    
        

        //*****************************************************************************************************************************************
        //GESTIONE FUNZIONALITA' BASE 
        //*****************************************************************************************************************************************
        
        private String searchHotel(String nomeHotel, String city) { //Ricavo le info di un hotel specifico
            for(String City : this.hotelMap.keySet()){
                if(City.equals(city)){
                    List<Hotel> hotelsInCity = hotelMap.get(city);                    
                    for(Hotel hotel : hotelsInCity){
                        if(hotel.name.equals(nomeHotel)){
                            //hotel cercato
                            return hotel.toString();
                        }
                    }
                }
            }
            return "Errore, Hotel con queste caratteristiche non trovato";
        }

        public String searchAllHotels(String city,SocketChannel client) throws Exception{ //Ricavo le info di tutti gli hotel di una particolare città
            for(String City : this.hotelMap.keySet()){
                if(City.equals(city)){
                    List<Hotel> hotelsInCity = hotelMap.get(city);                    
                    ByteBuffer bufferRisposta2 = ByteBuffer.wrap(stringifyHotelList(hotelsInCity).getBytes());
                    client.register(selector,SelectionKey.OP_WRITE, bufferRisposta2);    
                    return "OK, lista hotel prelevata";
                }
            }
            return "Errore, non esiste nessun hotel o città sconosciuta";

        }
        
        public boolean checkUsernamePassword(List<User> userList, String username, String password){ //controllo se l'username già esiste e se la relativa password corrisponde
            for(User user : userList){
                if(user.username.equals(username) && user.password.equals(password))
                    return true;
            }
            return false;
        }

        public boolean login(String username, String password){ //Funzione di login
            try {
                // Leggo la lista degli utenti dal file JSON
                List<User> userList = readUserListFromJson();

                if(checkUsernamePassword(userList,username,password)){ //controllo se l'username già esiste e se la relativa password corrisponde
                    //controllo che l'username specifico ha la stessa password inserita

                    
                    System.out.println("Nuovo utente loggato con successo.");   
                
                    return true;
                }else{
                    System.out.println("Password errata. Tentativo di accesso fallito");
                    return false;
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }


        }

        public boolean register(String username, String password){ //funzione di register
            try {
                // Leggo la lista degli utenti dal file JSON
                List<User> userList = readUserListFromJson();

                if(isUsernameAvailable(userList,username) && !password.isEmpty()){ //controllo se l'username è disponibile e se la password non è vuota
                    // Aggiungo il nuovo utente alla lista
                    userList.add(new User(username, password, "Recensore"));
                }else{
                    System.out.println("Username già usato da un altro utente");
                }
                
                // Scrivi la lista aggiornata nel file JSON
                writeUserListToJson(userList);

                System.out.println("Nuovo utente aggiunto con successo.");   
                return true; 
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

        }

        public boolean isUsernameAvailable(List<User> userList, String username){ //Controllo se l'username scelto per un nuovo utente è già usato
            for(User user: userList){
                if(user.getUsername().equals(username)){ //se esiste un occorrenza l'username non è utilizzabile
                    return false; 
                }
            }
            return true;
        }

        public String toString(){ //metodo toString delle info del server
            return "Server{"+
                    "address= "+this.address+' '+
                    "port= "+this.port+' '+
                    "PathHotel= "+this.pathHotels+' '+
                    "Status= "+this.serviceStatus+
                    '}';        
        }

        public void printAllHotels(ConcurrentHashMap<String, List<Hotel>> hotelMap){ //stampa su console la lista degli hotel in quel momento gestiti
            for(String city : hotelMap.keySet()){
                System.out.println(city);
                System.out.println("────────────────────────────────────────────────");
                List<Hotel> hotelsInCity = hotelMap.get(city);
                for(Hotel hotel : hotelsInCity){
                    //hotel di un particolare gruppo (particolare città)
                    System.out.println("ID: "+hotel.id);
                    System.out.println("Name: "+hotel.name);
                    System.out.println("Description: "+hotel.description);
                    System.out.println("City: "+hotel.city);
                    System.out.println("Phone: "+hotel.phone);
                    System.out.println("Services: "+hotel.services);
                    System.out.println("Rate: "+hotel.rate);
                    System.out.println("\t-cleaning: "+hotel.ratings.cleaning);
                    System.out.println("\t-services: "+hotel.ratings.services);
                    System.out.println("\t-position: "+hotel.ratings.position);
                    System.out.println("\t-quality: "+hotel.ratings.quality);
                    System.out.println("\t-LocalRanking: "+hotel.LocalRanking);
                    System.out.println("|-------------------------");
                    
                    
                }
                System.out.println("────────────────────────────────────────────────");
            }
        }
        
        
        //*****************************************************************************************************************************************
        //GESTIONE FUNZIONALITA' HOTEL 
        //*****************************************************************************************************************************************
        public void updateCountReviewHotel(String nomeHotel, String città){ //incremento il numero di recensioni collegate a uno specifico hotel
            try {
                System.out.println("STO AGGIORNANDO IL COUNT DELL'HOTEL "+ nomeHotel+ " "+città);
                for(String city : this.hotelMap.keySet()){ //Scorro tutte le città
                    if(city.equals(città)){ //se la città è uguale alla città in cui voglio cercare controllo la lista degli hotel in quella città
                        List<Hotel> hotelsInCity = this.hotelMap.get(city);
                        for(Hotel hotel : hotelsInCity){
                            if(hotel.name.equals(nomeHotel)){
                                System.out.print("nome hotel che modifico "+hotel.name);
                                hotel.numReview++;

                            }
                        }
                    }
                    
                }
                //Aggiorno in json la modifica dell'hotel
                this.convertHotelListToJson(convertHotelMapToList(this.hotelMap));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void updateRatings() throws IOException{ //aggiorno i ratings di tutti gli hotel
      
            //creo la lista di hotel di una città
            //prelevo per ogni hotel la lista delle loro recensioni
            //di ogni recensione faccio la media dei rating e la assegno agli attributi dell'hotel
            //ciclo
            for(String city: this.hotelMap.keySet()){
                List<Hotel> cityHotel = hotelMap.get(city);
                List<Review> cityReview = reviewMap.get(city);
                if (cityReview == null) {
                    cityReview = new ArrayList<>(); // Lista vuota se non ci sono recensioni per la città
                }
                for(Hotel hotel : cityHotel){
                    List<Review> hotelReviews = getHotelReviews(hotel, cityReview);
                    updateHotelRatingsWithAverage(hotel, hotelReviews); //aggiorno le statistiche dello specifico hotel
                }
            } 

            //update del json degli hotel
            this.convertHotelListToJson(convertHotelMapToList(this.hotelMap));


        }
        
        private void updateHotelRatingsWithAverage(Hotel hotel,List<Review> reviews) throws IOException { //aggiorno i ratings generali di uno specifico hotel
            if (reviews.isEmpty()) {
                return;
            }
            double totalRate = 0;
            double totalCleaning = 0;
            double totalPosition = 0;
            double totalServices = 0;
            double totalQuality = 0;

            int numReviews = reviews.size();

            for (Review review : reviews) {
                totalRate += review.getRate();
                totalCleaning += review.getRatings().getCleaning();
                totalPosition += review.getRatings().getPosition();
                totalServices += review.getRatings().getServices();
                totalQuality += review.getRatings().getQuality();
            }
            //Calcolo delle nuove medie
            double avgRate = Math.round(totalRate / numReviews * 100.0) / 100.0;
            double avgCleaning = Math.round(totalCleaning / numReviews * 100.0) / 100.0;
            double avgPosition = Math.round(totalPosition / numReviews * 100.0) / 100.0;
            double avgServices = Math.round(totalServices / numReviews * 100.0) / 100.0;
            double avgQuality = Math.round(totalQuality / numReviews * 100.0) / 100.0;
 
            //associamo le nuove medie ai rispettivi campi dello specifico hotel
            hotel.setRate(avgRate);
            hotel.getRatings().setCleaning(avgCleaning);
            hotel.getRatings().setPosition(avgPosition);
            hotel.getRatings().setQuality(avgQuality);
            hotel.getRatings().setServices(avgServices);
           
            //scrivo la lista degli hotel aggiornata in json
           // this.writeReviewListToJson(reviews); devo scrivere gli hotel
        }

        public List<Review> getHotelReviews(Hotel hotel, List<Review> reviews){ //Prelevo la lista di recensioni legate a un hotel specifico
            List<Review> hotelReviews = new ArrayList<>();
            for (Review review : reviews) {
                if (review.getNameHotel().equals(hotel.getName())) {
                    hotelReviews.add(review);
                }
            }
            //System.out.println("getHotelReviews "+hotel.name);
            //stampo cosa ho prelevato
            for(Review review : hotelReviews){
                //System.out.println(review.rate+" "+review.ratings.cleaning+" "+review.ratings.position+" "+review.ratings.quality+" "+review.ratings.services);
            }
            return hotelReviews;

        }
       
        public static String stringifyHotelList(List<Hotel> hotelChunk) { //A partire da una lista di hotel la rende String 
            StringBuilder stringBuilder = new StringBuilder();
            for (Hotel hotel : hotelChunk) {
                stringBuilder.append(hotel.toString()).append("\n");
            }
            return stringBuilder.toString();
        }

        public String stringifyHotelMap(){ //trasforma la hashmap degli hotel in una stringa
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            
            for (String city : this.hotelMap.keySet()) {
                sb.append("  \"").append(city).append("\": [");
                List<Hotel> cityHotels = this.hotelMap.get(city);
                for (Hotel hotel : cityHotels) {
                    sb.append("    ").append(hotel.toString(1)).append(",");
                }
                sb.append("  ],");
            }
            sb.append("}\n");
            return sb.toString();
        }

        public List<Hotel> readHotelListFromJson(String filePath) { //Legge dal json gli hotel e li rende una List<Hotel>
            try (Reader reader = new FileReader(new File(filePath))) {
                Gson gson = new Gson();
                Type hotelListType = new TypeToken<List<Hotel>>() {}.getType();
                return gson.fromJson(reader, hotelListType);
            } catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }
        
        public ConcurrentHashMap<String, List<Hotel>> organizeHotelsByCity(List<Hotel> hotelList) { //organizza la lista degli hotel in una HashMap
            ConcurrentHashMap<String, List<Hotel>> hotelMap = new ConcurrentHashMap<>();
            for (Hotel hotel : hotelList) {
                String city = hotel.getCity();
                hotelMap.computeIfAbsent(city, k -> new ArrayList<>()).add(hotel);
            }
            return hotelMap;
        }
    
        private List<Hotel> convertHotelMapToList(ConcurrentHashMap<String, List<Hotel>> hotelMap) { //Trasformo l'intera HashMap degli hotel in una lista di hotel
            List<Hotel> hotelList = new ArrayList<>();
            for (List<Hotel> cityHotels : hotelMap.values()) {
                hotelList.addAll(cityHotels);
            }
            return hotelList;
        }

        private void convertHotelListToJson(List<Hotel> hotelList) { //Dato una lista di hotel la scrivo nel json
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(this.pathHotels)) {
                gson.toJson(hotelList, writer);
                System.out.println("Lista degli hotel scritta nel file " + this.pathHotels);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }    

        //*****************************************************************************************************************************************
        //GESTIONE Utenti 
        //*****************************************************************************************************************************************
        public void updateCountReviewUser(String username){ //Aggiornamento del numero di recensioni fatte da un utente e del suo relativo badge
            try {
                // Leggo la lista degli utenti dal file JSON
                List<User> userList = readUserListFromJson();
                
                //cerco l'utente con quello specifico username
                for(User user: userList){
                    if(user.username.equals(username)){
                        user.numReview++;
                        if(user.numReview>=0 && user.numReview<=2)
                            user.badge  = "Recensore";
                        else if (user.numReview>=3 && user.numReview<=5)
                            user.badge  = "Recensore Esperto";
                        else if (user.numReview>=6 && user.numReview<=8)
                            user.badge  = "Contributore";
                        else if (user.numReview>=9 && user.numReview<=10)
                            user.badge  = "Contributore Esperto";
                        else if (user.numReview>=11)
                            user.badge  = "Contributore Super";
                    }

                }

                // Scrivi la lista aggiornata nel file JSON
                writeUserListToJson(userList);

            } catch (Exception e) {
                e.printStackTrace();
            }
            

        }
      
        public void writeUserListToJson(List<User> userList) throws IOException{ //scrivo una lista di utenti nel json
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            File file = new File(this.pathUsers);
    
            // Scrivi la lista degli utenti nel file JSON
            Writer writer = new FileWriter(file);
            gson.toJson(userList, writer);
            writer.close();    
        }
     
        public List<User> readUserListFromJson() throws IOException{ //leggo dal file json la lista di utenti
            Gson gson = new Gson();
            File file = new File(this.pathUsers);
    
            // Se il file non esiste, restituisci una lista vuota
            if (!file.exists()) {
                return new Vector<>();
            }
    
            // Leggi la lista degli utenti dal file JSON
            Reader reader = new FileReader(file);
            Type userListType = new TypeToken<List<User>>() {}.getType();
            return gson.fromJson(reader, userListType);

        }
    
        public String takeBadgeofUser(String username) throws IOException{ //Prelevo il badge di un particolare utente tramite il suo username
            // Leggo la lista degli utenti dal file JSON
            List<User> userList = readUserListFromJson();

            for(User user : userList){
                 if(user.username.equals(username)){
                     System.out.println(user.numReview);
                     return user.badge;
                 }
            }
            return "Errore, utente non trovato";
     }
    }

 
  
    public static void main(String[] args) throws Exception{

         //nuovo oggetto Properties
         Properties prop = new Properties();    
         try {
             //carico il file di configurazione
             prop.load(new FileInputStream("src/config_server.dat"));
         } catch (IOException e) {
             e.printStackTrace();
         }

   
    
        //nuovo oggetto server 
        Server server = new Server(prop);

   
        //creazione hashmap iniziale degli hotel*************************************************

        // Leggi il file JSON e ottieni la lista di hotel
        List<Hotel> hotelList = server.readHotelListFromJson(server.pathHotels);

        // Organizza gli hotel per città in una ConcurrentHashMap
        server.hotelMap = server.organizeHotelsByCity(hotelList);


        //Creazione hashmap delle recensioni******************************************************
        // Leggi il file JSON e ottieni la lista delle recensioni
        List<Review> reviewList = server.readReviewsListFromJson(server.pathReviews);
        //Organizza le recensioni per città in una ConcurrentHashMap
        server.reviewMap = server.groupReviewsByCity(reviewList);

        
        //Creazione del thread periodico
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        int initialDelay = 0; // Tempo di attesa prima dell'esecuzione del task
        Runnable serverTask = new MulticastSender(server.groupUDP,server.portUDP,server);
        scheduler.scheduleAtFixedRate(serverTask, initialDelay, server.period_scheduled , TimeUnit.SECONDS);


        //server.printAllHotels(server.hotelMap);
       
        System.out.println("\n To string : "+server.toString());
        //init, get e close servizio server
        server.initService();
        server.serviceGest();
        server.closeService();
    }
}