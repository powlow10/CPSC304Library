package library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;


public class LibrarianActions extends UserActions {

	Vector columnNames = new Vector();
	Vector data = new Vector();
	JPanel panel = new JPanel();

	public LibrarianActions(Connection c) {
		super(c);
	}

	//	Add a new book to the library
	//			INSERT INTO Book (callNumber, isbn, title, mainAuthor, publisher, year )
	//				VALUES(1, 2, 3, 4, 5, 6);
	//
	//	1 = callNumber
	//	2 = isbn
	//	3 = title
	//	4 = mainAuthor
	//	5 = publisher
	//	6 = year
	public void addBook(String callNumber, String isbn, String title, String mainAuthor,
			String publisher, int year) {
		PreparedStatement ps = null;
		try{
			ps = con.prepareStatement("INSERT INTO Book VALUES (?,?,?,?,?,?)");

			ps.setString(1, callNumber);
			ps.setString(2, isbn);
			ps.setString(3, title);
			ps.setString(4, mainAuthor);
			ps.setString(5, publisher);
			ps.setInt(6, year);

			// update the database
			ps.executeUpdate();
			// commit work
			con.commit();
			ps.close();

		} catch (SQLException ex) {
			System.out.println("Message: " + ex.getMessage());
			try {
				// undo the insert
				con.rollback();
			} catch (SQLException ex2) {
				System.out.println("Message: " + ex2.getMessage());
				System.exit(-1);
			}
		}
	}

	// 	Add a new copy of an existing book to the library
	//			INSERT INTO BookCopy (callNumber, copyNo, status)
	//				VALUES(1, 2, 3);
	//
	//	1 = callNumber (MUST ALREADY EXIST)
	//	2 = isbn
	//	3 = status
	public void addCopy(String callNumber, String copyNo) {
		PreparedStatement ps = null;
		String status = "IN";
		try{
			ps = con.prepareStatement("INSERT INTO BookCopy VALUES (?,?,?)");

			ps.setString(1, callNumber);
			ps.setString(2, copyNo); // Should Increment TODO ex. C1, C2, C3... etc.
			ps.setString(3, status); // All Book Copies are set to "IN" status

			// update the database
			ps.executeUpdate();
			// commit work
			con.commit();
			ps.close();

		} catch (SQLException ex) {
			System.out.println("Message: " + ex.getMessage());
			try {
				// undo the insert
				con.rollback();
			} catch (SQLException ex2) {
				System.out.println("Message: " + ex2.getMessage());
				System.exit(-1);
			}
		}

	}

	//	Generate report with all books that have been checked out
	//	shows also dates checked out and due dates
	//	flags overdue items << In java code?
	//		SELECT BookCopy.callNumber, BookCopy.copyNo, bid, status, outdate, indate
	//		FROM BookCopy JOIN Borrowing
	//		ON (BookCopy.callNumber = Borrowing.callNumber) AND (BookCopy.copyNo = Borrowing.copyNo)
	//		WHERE BookCopy.status LIKE 'out'
	public void generateCheckedOutReport() {
		PreparedStatement ps = null;
		try{
			ps = con.prepareStatement("SELECT BookCopy.callNumber, BookCopy.copyNo, bid, status, outdate, indate " +
					"FROM BookCopy JOIN Borrowing " +
					"ON (BookCopy.callNumber = Borrowing.callNumber) " +
					"AND (BookCopy.copyNo = Borrowing.copyNo) " +
					"WHERE BookCopy.status LIKE 'out'");


			// produce view
			ResultSet resultSearch = ps.executeQuery();
			ResultSetMetaData rsmd = ps.getMetaData();


			// create table panel
			int numCols = rsmd.getColumnCount();
			for (int i = 0; i < numCols; i++) {
				// get column name and print it
				columnNames.addElement(rsmd.getColumnName(i+1));
				System.out.printf("%-15s", rsmd.getColumnName(i + 1));
			}

			while (resultSearch.next()) {
				Vector row = new Vector(numCols);
				for (int i = 1; i <= numCols; i++) {
					row.addElement(resultSearch.getObject(i));
				}
				data.addElement(row);

			}


			resultSearch.close();
			JTable table = new JTable(data, columnNames);
			TableColumn column;
			for (int i = 0; i < table.getColumnCount(); i++) {
				column = table.getColumnModel().getColumn(i);
				column.setMaxWidth(250);
			}
			JScrollPane scrollPane = new JScrollPane(table);
			panel.add(scrollPane);               
			JFrame frame = new JFrame();
			frame.add(panel);         //adding panel to the frame
			frame.setSize(600, 400); //setting frame size
			frame.setVisible(true);

			// commit work
			con.commit();
			ps.close();

		} catch (SQLException ex) {
			System.out.println("Message: " + ex.getMessage());
			try {
				// undo the insert
				con.rollback();
			} catch (SQLException ex2) {
				System.out.println("Message: " + ex2.getMessage());
				System.exit(-1);
			}
		}
	}

	//	Generate report with "N" most popular items for a given year "Y"
	//	n = top N, y = year
	//		CREATE OR REPLACE VIEW top AS
	//		SELECT *
	//		FROM
	//			(SELECT callNumber, count(callNumber) as count
	//			FROM
	//				(SELECT *
	//				FROM Borrowing
	//				WHERE outdate LIKE [Y] + '%')
	//			GROUP BY callNumber)
	//		WHERE rownum <= [N]
	//		ORDER BY count DESC;
	//
	//		SELECT title, count 
	//		FROM book JOIN top
	//		ON book.callNumber = top.callNumber;
	public void generateMostPopularReport(int n, int y) {
		PreparedStatement getTop = null;
		PreparedStatement getTitle = null;
		int yDigit = (y % 100);		// isolates last 2 digits ex. 2012 -> 12, 1992 -> 92
		String justYear = new Integer(yDigit).toString();
		String nRows = new Integer(n).toString();
		try{
			// Creates a View of CallNumber, count called top
			String getTopString =
					"CREATE OR REPLACE VIEW top AS " +
							"SELECT * FROM " +
							"(SELECT callNumber, count(callNumber) as count " +
							"FROM (SELECT * FROM Borrowing WHERE outdate LIKE '" + justYear + "%')" +
							"GROUP BY callNumber) " +
							"WHERE rownum <= " + nRows + " " +
							"ORDER BY count DESC; ";
			getTop = con.prepareStatement(getTopString);

			getTop.executeUpdate(); // update database

			// Joins View TOP with table BOOK and grabs titles and counts
			String getTitleString = 
					"SELECT title, count " +
							"FROM book JOIN top " +
							"ON book.callNumber = top.callNumber;";
			getTitle = con.prepareStatement(getTitleString);

			ResultSet resultSearch = getTitle.executeQuery(); // update database
			ResultSetMetaData rsmd = resultSearch.getMetaData();


			// create table panel
			int numCols = rsmd.getColumnCount();
			for (int i = 0; i < numCols; i++) {
				// get column name and print it
				columnNames.addElement(rsmd.getColumnName(i+1));
				System.out.printf("%-15s", rsmd.getColumnName(i + 1));
			}

			while (resultSearch.next()) {
				Vector row = new Vector(numCols);
				for (int i = 1; i <= numCols; i++) {
					row.addElement(resultSearch.getObject(i));
				}
				data.addElement(row);

			}


			resultSearch.close();
			JTable table = new JTable(data, columnNames);
			TableColumn column;
			for (int i = 0; i < table.getColumnCount(); i++) {
				column = table.getColumnModel().getColumn(i);
				column.setMaxWidth(250);
			}
			JScrollPane scrollPane = new JScrollPane(table);
			panel.add(scrollPane);               
			JFrame frame = new JFrame();
			frame.add(panel);         //adding panel to the frame
			frame.setSize(600, 400); //setting frame size
			frame.setVisible(true);

			// commit work
			con.commit();
			getTop.close();
			getTitle.close();

		} catch (SQLException ex) {
			System.out.println("Message: " + ex.getMessage());
			try {
				// undo the insert
				con.rollback();
			} catch (SQLException ex2) {
				System.out.println("Message: " + ex2.getMessage());
				System.exit(-1);
			}
		}

	}
}


