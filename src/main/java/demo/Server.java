package demo;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class Server extends JFrame {
	private JTextField firstField;
	private JTextField lastField;
	private JTextField yearField;
	private JLabel message;

	public Server() {
		setResizable(false);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("Server");
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0 };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0 };
		getContentPane().setLayout(gridBagLayout);

		JLabel lblNewLabel = new JLabel("First Name: ");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel.gridx = 0;
		gbc_lblNewLabel.gridy = 0;
		getContentPane().add(lblNewLabel, gbc_lblNewLabel);

		firstField = new JTextField();
		firstField.setEditable(false);
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.insets = new Insets(0, 0, 5, 0);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 0;
		getContentPane().add(firstField, gbc_textField);
		firstField.setColumns(30);

		JLabel lblNewLabel_1 = new JLabel("Last Name : ");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 0;
		gbc_lblNewLabel_1.gridy = 1;
		getContentPane().add(lblNewLabel_1, gbc_lblNewLabel_1);

		lastField = new JTextField();
		lastField.setEditable(false);
		GridBagConstraints gbc_textField_1 = new GridBagConstraints();
		gbc_textField_1.insets = new Insets(0, 0, 5, 0);
		gbc_textField_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_1.gridx = 1;
		gbc_textField_1.gridy = 1;
		getContentPane().add(lastField, gbc_textField_1);
		lastField.setColumns(30);

		JLabel lblNewLabel_2 = new JLabel("Year : ");
		GridBagConstraints gbc_lblNewLabel_2 = new GridBagConstraints();
		gbc_lblNewLabel_2.anchor = GridBagConstraints.EAST;
		gbc_lblNewLabel_2.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_2.gridx = 0;
		gbc_lblNewLabel_2.gridy = 2;
		getContentPane().add(lblNewLabel_2, gbc_lblNewLabel_2);

		yearField = new JTextField();
		yearField.setEditable(false);
		GridBagConstraints gbc_textField_2 = new GridBagConstraints();
		gbc_textField_2.insets = new Insets(0, 0, 5, 0);
		gbc_textField_2.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField_2.gridx = 1;
		gbc_textField_2.gridy = 2;
		getContentPane().add(yearField, gbc_textField_2);
		yearField.setColumns(4);

		message = new JLabel(" ");
		GridBagConstraints gbc_lblNewLabel_3 = new GridBagConstraints();
		gbc_lblNewLabel_3.gridwidth = 2;
		gbc_lblNewLabel_3.insets = new Insets(10, 10, 20, 10);
		gbc_lblNewLabel_3.gridx = 0;
		gbc_lblNewLabel_3.gridy = 3;
		getContentPane().add(message, gbc_lblNewLabel_3);

		pack();
	}

	private static String toHex(byte[] bytes) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < bytes.length; i++)
			s.append(String.format("%02x", bytes[i]));
		return s.toString();
	}

	private static String extract(String line) {
		line = line.split(" : ", 2)[1];
		return line.substring(1, line.lastIndexOf('"'));
	}

	private static String message(String first, String last, String year, String checksum) {
		byte[] bytesOfMessage;
		try {
			bytesOfMessage = (first + last + year).getBytes("UTF-8");
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] thedigest = md.digest(bytesOfMessage);
			return (checksum.equals(toHex(thedigest))) ? "Checksum - Valid" : "Checksum - Invalid"; 
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			return "Checksum - Invalid";
		}
	}

	public static void main(String[] args) {
		if (args == null || args.length != 2) {
			System.err.println("Usage: java -cp target/mallet-1.0-SNAPSHOT.jar demo.Server hostname port");
			System.exit(1);
		}
		InetSocketAddress listen = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		Server server = new Server();
		server.setVisible(true);
		ServerSocket ss = null;
		try {
			ss = new ServerSocket(listen.getPort(), 1, listen.getAddress());
			Socket s;
			while ((s = ss.accept()) != null) {
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
				BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

				try {
					String first, last, year, checksum;
					in.readLine();
					year = extract(in.readLine());
					checksum = extract(in.readLine());
					first = extract(in.readLine());
					last = extract(in.readLine());
					in.readLine();
					server.firstField.setText(first);
					server.lastField.setText(last);
					server.yearField.setText(year);
					server.message.setText(message(first, last, year, checksum));
	
					out.write("{\"age\":\"" + (Calendar.getInstance().get(Calendar.YEAR)-Integer.parseInt(year)) + "\"}\n");
					out.flush();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					s.close();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {}
			}
		}
	}
}
