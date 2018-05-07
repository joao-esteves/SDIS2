package database;

import java.io.IOException;
import java.sql.*;

import utils.Utils;

public class Database {

  // define the driver to use 
     private String driver = "org.apache.derby.jdbc.EmbeddedDriver";
  // the database name  
     private String dbName="localDB";
  // define the Derby connection URL to use 
     private String connectionURL = "jdbc:derby:" + dbName + ";create=true";
     
     private String initScript = "initDB.sql";
     
     private Connection conn = null;
     
     
     public Database(){
    	 connect();
    	 if (!checkDBExisted()) {
    		 loadDB();
    	 }
    	 
     }
     private boolean checkDBExisted() {
		try {
			
			DatabaseMetaData metadata = conn.getMetaData();
	    	ResultSet tables = metadata.getTables(conn.getCatalog(), null, "FILESSTORED", null);
	    	boolean tableExists = tables.next();
	    	Utils.log("DB existed: " + tableExists);
	    	return tableExists;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
    	 
     }
     
     public void connect() {
    		 try {
				conn = DriverManager.getConnection(connectionURL);
				Utils.log("Connected to database " + dbName);
				
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}  	
	}
     
    public Connection getConnection() {
    	return conn;
    }
     
    public void closeConnection() {
    	if (driver.equals("org.apache.derby.jdbc.EmbeddedDriver")) {
            boolean gotSQLExc = false;
            try {
               DriverManager.getConnection("jdbc:derby:;shutdown=true");
            } catch (SQLException se)  {	
               if ( se.getSQLState().equals("XJ015") ) {		
                  gotSQLExc = true;
               }
            }
            if (!gotSQLExc) {
            	  Utils.log("Database did not shut down normally");
            }  else  {
               Utils.log("Database shut down normally");	
            }  
         }
    }
     
    public void loadDB() {
    	try {
			String sqlScript = Utils.readFile(initScript);
			runScript(sqlScript);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
     
    private void runScript(String sql) {
    	try {
			Statement stmt = conn.createStatement();
			String[] stmtList = sql.split(";");
			for (int i = 0; i < stmtList.length; i++) {
				String currentStmt = stmtList[i].trim();
				if (!currentStmt.isEmpty()) {
					stmt.execute(stmtList[i].trim());	
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}	
    }
}
