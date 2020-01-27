package org.sciplore.batch;

import com.google.common.hash.Hashing;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.dbcp2.BasicDataSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.sql.Date;
import java.text.DateFormat;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;

import static org.sciplore.batch.BatchMain.*;

public class BatchObfuscator {
    // Connection Pool
    // TODO: remove hardcoded user, password, and server
    private static BasicDataSource ds = new BasicDataSource();

    static {
        ds.setUrl("jdbc:mariadb://127.0.0.1:3306/hyplag");
        ds.setUsername("root");
        ds.setPassword("mariadbroot");
        ds.setMinIdle(12);
        ds.setMaxIdle(40);
        ds.setMaxOpenPreparedStatements(1000);
        ds.setAutoCommitOnReturn(false);
    }

    public static Connection getPooledConnection() throws SQLException {
        return ds.getConnection();
    }

    public void JDBCDetection(CommandLine finalCmd, String user, String pw) throws SQLException, ExecutionException, InterruptedException {
        ds.setUrl(finalCmd.getOptionValue("server", DEFAULT_SERVER_ADDR));
        ds.setUsername(user);
        ds.setPassword(pw);
        createIndexDocID(finalCmd);
        createIndexHash(finalCmd);
        createResultsTable(finalCmd);

        // Start Detection on Database
        String startTime = "Detection Start Time: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis()));
        out.println(startTime);



        // Parallel Stream Execution
        AtomicInteger progress = new AtomicInteger();
        List<Integer> range = IntStream.rangeClosed(Integer.parseInt(finalCmd.getOptionValue("next", DEFAULT_NEXT)), Integer.parseInt(finalCmd.getOptionValue("end", DEFAULT_END)))
                .boxed().collect(Collectors.toList());
        ForkJoinPool customThreadPool = new ForkJoinPool(Integer.parseInt(finalCmd.getOptionValue("parallel", DEFAULT_NUMBER_OF_THREADS)));
        customThreadPool.submit(() -> range.parallelStream().forEachOrdered(docNum -> {
            try {
                if(DEFAULT_SAMPLES){
                    docNum = 37591;
                }
                Connection connection = getPooledConnection();

                progress.getAndIncrement();
                // Get hash list of source document
                List<String> sourceDocHashes = new LinkedList<>();
                Statement pstmt = connection.createStatement();
                ResultSet prs = pstmt.executeQuery(" SELECT HEX(hash_value) from k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple where doc_id = " + docNum);
                while (prs.next()) {
                    // get hash
                    String hash = prs.getString("HEX(hash_value)");
                    if (hash == null) {
                        continue;
                    }
                    sourceDocHashes.add(hash);
                }
                sourceDocHashes = sourceDocHashes.stream().distinct().collect(Collectors.toList());
                prs.close();
                pstmt.close();

                // Get all candidate docs who share the hash
                List<Integer> candidates = getAllCandidateDocs(finalCmd, connection, sourceDocHashes, docNum);

                // Matching
                matchWithCandidates(finalCmd, docNum, sourceDocHashes, connection, candidates);
                connection.close();

                out.println("Progress: "+ progress + " Document " + docNum + " finished at: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis())));
                connection.close();
                if(DEFAULT_SAMPLES){
                    System.exit(1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        })).get();
        out.println(startTime);
        out.println("End: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis())));
        out.println("============== FINISH =================");

        System.exit(1); //TODO: properly exclude next case
    }

    private void createIndexHash(CommandLine finalCmd) throws SQLException {
        out.println("... now creating hash index");

        Connection connection = null;
        Statement stmt = null;
        try {
            connection = getPooledConnection();
            stmt = connection.createStatement();
            stmt.executeQuery(
                    "alter table k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple add index if not EXISTS  hash_index (hash_value);");
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        out.println("Ready for: Detections");
        connection.close();
    }

    private void matchWithCandidates(CommandLine finalCmd, Integer docNum, List<String> sourceDocHashes, Connection connection, List<Integer> candidates) throws SQLException {
        for (int candidate : candidates) {
            // Get hash lists of candidate docs
            List<String> candidateHashes = new LinkedList<>();
            Statement cstmt = connection.createStatement();
            ResultSet crs = cstmt.executeQuery(" SELECT HEX(hash_value) from k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple where doc_id = " + candidate);
            while (crs.next()) {
                String hash = crs.getString("HEX(hash_value)");
                if (hash == null) {
                    continue;
                }
                candidateHashes.add(hash);
            }
            candidateHashes = candidateHashes.stream().distinct().collect(Collectors.toList());
            cstmt.close();
            crs.close();

            // Intersection
            Set<String> results = new HashSet<>();
            for (String sh : sourceDocHashes) {
                for (String ch : candidateHashes) {
                    if (sh.equals(ch)) {
                        // matching reference found, now compute the additional information
                        results.add(sh);
                        break;
                    }
                }
            }

            // Get Relative BCS
            double bcs = 0d;
//            // Lists to get all hashes that could have been matches
//            List<String> combinedList = Stream.of(sourceDocHashes, candidateHashes)
//                    .flatMap(x -> x.stream())
//                    .collect(Collectors.toList());
//            combinedList = combinedList.stream().distinct().collect(Collectors.toList());

            int k = Integer.parseInt(finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE));
            double absStrengthK1 = results.size();
            double srcHashListK1 = sourceDocHashes.size();
            double canHashListk1 = candidateHashes.size();
            // Reconstructing K1
            if(k>1){
                // get common denominator-1
                absStrengthK1 = Math.ceil(Math.pow(absStrengthK1, 1.0/k));
                srcHashListK1 = Math.ceil(Math.pow(srcHashListK1, 1.0/k));
                canHashListk1 = Math.ceil(Math.pow(canHashListk1, 1.0/k));
            }


            PreparedStatement insert = connection.prepareStatement("INSERT INTO k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_results (src_id, sel_id, bcs) VALUES (?,?,?)");
            if(results.size()>0) {
                bcs =  (double) absStrengthK1/ (srcHashListK1 + canHashListk1 - absStrengthK1) ;

                // prepare insert - for batch insert in results
                insert.setInt(1, docNum);
                insert.setInt(2, candidate);
                insert.setDouble(3, bcs);
                insert.addBatch();
            }
            insert.executeBatch();
            insert.getConnection().commit();
        }
    }

    private List<Integer> getAllCandidateDocs(CommandLine finalCmd, Connection connection, List<String> sourceDocHashes, int sourceDoc) throws SQLException {
        List<Integer> candidates = new LinkedList<>();
        // for each hash
        for (String hash : sourceDocHashes) {
            Statement hstmt = connection.createStatement();
            ResultSet hrs = hstmt.executeQuery(" SELECT * from k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple where hash_value = UNHEX('" + hash + "')");
            while (hrs.next()) {
                // get hash
                Integer docid = hrs.getInt("doc_id");
                if (sourceDoc != docid) {
                    candidates.add(docid);
                }
            }
            hstmt.close();
            hrs.close();
        }
        candidates = candidates.stream().distinct().collect(Collectors.toList());

        return candidates;
    }

    private void createResultsTable(CommandLine finalCmd) {
        out.println("... now creating k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_results Table");

        Connection connection = null;
        Statement stmt = null;
        try {
            connection = getPooledConnection();
            stmt = connection.createStatement();
            stmt.executeQuery(
                    "create table if not EXISTS k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_results (" +
                            "src_id MEDIUMINT NOT NULL," +
                            "sel_id MEDIUMINT NOT NULL," +
                            "bcs double  NOT NULL" +
                            ");");
            connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createIndexDocID(CommandLine finalCmd) {
        out.println("... now creating doc_id index");

        Connection connection = null;
        Statement stmt = null;
        try {
            connection = getPooledConnection();
            stmt = connection.createStatement();
            stmt.executeQuery(
                    "alter table k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple add index if not EXISTS  doc_index (doc_id);");
            connection.commit();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        out.println("Ready for: Detections");
    }

    public void tuplesToTable(CommandLine finalCmd, String user, String password, String url) throws SQLException, ExecutionException, InterruptedException {
        out.println("Tuple Size: " + finalCmd.getOptionValue("ktuple", DEFAULT_KTUPLE));
        out.println("Number of Threads and Connections: " + finalCmd.getOptionValue("parallel", DEFAULT_NUMBER_OF_THREADS));

        ds.setUsername(user);
        ds.setPassword(password);
        ds.setUrl(url);

        createResultTable(finalCmd);

        // Start Table insertions
        String startTime = "Start Time: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis()));
        out.println(startTime);

        // Files
        File inputFolder = new File(System.getProperty("user.home") + "/csv");
        File[] listOfFiles = inputFolder.listFiles();

        out.println("Connected to MariaDB...");
        String currentTable = finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE);
        currentTable = "k" + currentTable + "_tuple";

        LinkedList<File> llof = new LinkedList<>(Arrays.stream(listOfFiles).collect(Collectors.toList()));
        String finalCurrentTable = currentTable;

        AtomicInteger progress = new AtomicInteger();

        ForkJoinPool customThreadPool = new ForkJoinPool(Integer.parseInt(finalCmd.getOptionValue("parallel", DEFAULT_NUMBER_OF_THREADS)));
        customThreadPool.submit(() -> llof.parallelStream().forEach(doc -> {
            progress.incrementAndGet();
            Connection connection = null;
            try {
                connection = getPooledConnection();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            File f = new File(doc.toString());
            if (f.isFile()) {
                out.println("File " + f.getName() + "Progress: " + progress);
                Statement stmt = null;
                try {

                    stmt = connection.createStatement();
                    stmt.executeQuery(
                            "LOAD DATA LOCAL INFILE \'" + f + "\' INTO TABLE " + finalCurrentTable + " CHARACTER SET UTF8 FIELDS TERMINATED BY ',' LINES TERMINATED BY '\n' IGNORE 1 LINES (doc_id, @hash_value) SET hash_value = UNHEX(@hash_value);");
                    connection.commit();
                } catch (SQLException ex) {
                    // handle any errors
                    System.out.println("SQLException: " + ex.getMessage());
                    System.out.println("SQLState: " + ex.getSQLState());
                    System.out.println("VendorError: " + ex.getErrorCode());
                }

            } else if (f.isDirectory()) {
                System.out.println("Not a File: " + f.getName());
            }
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        })).get();


        out.println(startTime);
        out.println("End: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis())));
        out.println("============== FINISH =================");
    }


    public void obfuscate(CommandLine finalCmd, String user, String password, String url) throws InterruptedException, ExecutionException {
        out.println("Tuple Size: " + finalCmd.getOptionValue("ktuple", DEFAULT_KTUPLE));
        out.println("Max References per Document: " + finalCmd.getOptionValue("refs", DEFAULT_MAX_REFS));
        out.println("Number of Threads and Connections: " + finalCmd.getOptionValue("parallel", DEFAULT_NUMBER_OF_THREADS));

        ds.setUsername(user);
        ds.setPassword(password);
        ds.setUrl(url);

        // Try to create tuples table in case of direct insertion using "database"
        if (finalCmd.hasOption("database")) {
            createResultTable(finalCmd);
        }

        // Start Obfuscation
        String startTime = "Start Time: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis()));
        out.println(startTime);

        // Parallel Stream Execution
        List<Integer> range = IntStream.rangeClosed(Integer.parseInt(finalCmd.getOptionValue("next", DEFAULT_NEXT)), Integer.parseInt(finalCmd.getOptionValue("end", DEFAULT_END)))
                .boxed().collect(Collectors.toList());
        ForkJoinPool customThreadPool = new ForkJoinPool(Integer.parseInt(finalCmd.getOptionValue("parallel", DEFAULT_NUMBER_OF_THREADS)));
        customThreadPool.submit(() -> range.parallelStream().forEach(docNum -> {
            try {
                Connection connection = getPooledConnection();
                // Get all refs of 1 document
                Statement pstmt = connection.createStatement();
                ResultSet prs = pstmt.executeQuery("SELECT DISTINCT document_id, title from document_reference where document_id=" + docNum + " order by document_id desc");

                // Fetch each row from the result set
                LinkedList<String> refList = new LinkedList<>();
                while (prs.next()) {
                    String title = prs.getString("title");
                    if (title == null) {
                        continue;
                    }
                    title = Normalizer.normalize(title.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\s+-+","");
                    title = Hashing.sha1().hashString(title, StandardCharsets.UTF_8).toString();

                    refList.add(title);
                }
                connection.close();
                // ---
                connection = getPooledConnection();
                PreparedStatement ps = null;
                StringBuilder builder = new StringBuilder();
                String ColumnNamesList = "doc_id,hash_value";
                if (finalCmd.hasOption("database")) {
                    ps = connection.prepareStatement("INSERT INTO k" + finalCmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple (doc_id, UNHEX(hash_value)) VALUES (?, ?)");
                } else {
                    builder.append(ColumnNamesList + "\n");
                }

                // TODO: create generic recursive tuple builders for all k
                if (refList.size() > 0 && refList.size() <= Integer.parseInt(finalCmd.getOptionValue("refs", DEFAULT_MAX_REFS))) {
                    if (Integer.parseInt(finalCmd.getOptionValue("ktuple", DEFAULT_KTUPLE)) == 3) {
                        getTuples3(finalCmd, docNum, ps, builder, refList);
                    } else if (Integer.parseInt(finalCmd.getOptionValue("ktuple", DEFAULT_KTUPLE)) == 2) {
                        getTuples2(finalCmd, docNum, ps, builder, refList);
                    } else if (Integer.parseInt(finalCmd.getOptionValue("ktuple", DEFAULT_KTUPLE)) == 1) {
                        getHashes(finalCmd, docNum, ps, builder, refList);
                    }

                    // Batch insert after each document
                    if (finalCmd.hasOption("database")) {
                        ps.executeBatch();
                        connection.commit();
                        ps.close();
                    } else {
                        File homedir = new File(System.getProperty("user.home") + "/csv");
                        homedir.mkdir();
                        if (builder.toString().length() > (ColumnNamesList.length())) { //
                            File currentFile = new File("" + homedir + "/doc_" + docNum + "_k" + finalCmd.getOptionValue("ktuple", DEFAULT_KTUPLE) + "_tuple.csv");
                            try {
                                PrintWriter pw;
                                pw = new PrintWriter(currentFile);
                                pw.write(builder.toString());
                                pw.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                out.println("Document " + docNum + " finished at: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis())));
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        })).get();
        out.println(startTime);
        out.println("End: " + DateFormat.getDateTimeInstance().format(new Date(currentTimeMillis())));
        out.println("============== FINISH =================");
    }

    private void createResultTable(CommandLine cmd) {
        // Try to create table
        Connection connection = null;
        Statement stmt = null;
        try {
            connection = getPooledConnection();
            out.println("Connected to MariaDB...");
            stmt = connection.createStatement();
            stmt.executeQuery(
                    "create table if not EXISTS k" + cmd.getOptionValue("tupleSize", DEFAULT_KTUPLE) + "_tuple (\n" +
                            "\tdoc_id MEDIUMINT NOT NULL,\n" +
                            "\thash_value BINARY(20)  NOT NULL\n" +
                            ");");
            out.println("Table created...");
            connection.commit();
            connection.close();
        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
    }

    private void getHashes(CommandLine finalCmd, Integer docNum, PreparedStatement ps, StringBuilder builder, LinkedList<String> refList) throws SQLException {
        // tuple factory
        for (String k1 : refList) {
            //no empty titles, force 1 distinct attribute
            if (k1 != null && (k1 != "")) {
                // Prepare Batch insertion
                if (finalCmd.hasOption("database")) {
                    ps.setInt(1, docNum);
                    ps.setString(2, k1);
                    ps.addBatch();
                } else {
                    // prepare file
                    builder.append(docNum + "," + k1);
                    builder.append('\n');
                }
            }
        }
    }

    private void getTuples2(CommandLine finalCmd, Integer docNum, PreparedStatement ps, StringBuilder builder, LinkedList<String> refList) throws SQLException {
        // tuple factory
        for (String k1 : refList) {
            for (String k2 : refList) {
                //no empty titles, force 1 distinct attribute
                if (k1 != null && k1 != k2 && (k1 != "")) {
                    String tuple = k1 + k2;
                    String hash = Hashing.sha1().hashString(tuple, StandardCharsets.UTF_8).toString();
                    // Prepare Batch insertion
                    if (finalCmd.hasOption("database")) {
                        ps.setInt(1, docNum);
                        ps.setString(2, hash);
                        ps.addBatch();
                    } else {
                        // prepare file
                        builder.append(docNum + "," + hash);
                        builder.append('\n');
                    }
                }
            }
        }
    }

    private void getTuples3(CommandLine finalCmd, Integer docNum, PreparedStatement ps, StringBuilder builder, LinkedList<String> refList) throws SQLException {
        // tuple factory
        for (String k1 : refList) {
            for (String k2 : refList) {
                for (String k3 : refList) {
                    //no empty titles, force 1 distinct attribute
                    if (k1 != null && k1 != k3 && (k1 != "")) {
                        String tuple = k1 + k2 + k3;
                        String hash = Hashing.sha1().hashString(tuple, StandardCharsets.UTF_8).toString();

                        // Prepare Batch insertion
                        if (finalCmd.hasOption("database")) {
                            ps.setInt(1, docNum);
                            ps.setString(2, hash);
                            ps.addBatch();
                        } else {
                            // prepare file
                            builder.append(docNum + "," + hash);
                            builder.append('\n');
                        }
                    }
                }
            }
        }
    }
}
