package demo;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

public class Client extends JFrame {
	private JTextField firstField;
	private JTextField lastField;
	private JTextField yearField;
	private JLabel message;
	private JButton btnSend;

	private InetSocketAddress target;
	private Proxy proxy;

	public Client(InetSocketAddress target, Proxy proxy) {
		this.target = target;
		this.proxy = proxy;
		
		setResizable(false);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("Client");
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
		gbc_lblNewLabel_3.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_3.gridx = 0;
		gbc_lblNewLabel_3.gridy = 3;
		getContentPane().add(message, gbc_lblNewLabel_3);

		btnSend = new JButton("Send");
		btnSend.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				btnSend.setEnabled(false);
				new SwingWorker<String, Object>() {

					@Override
					protected String doInBackground() throws Exception {
						return getResponse(firstField.getText(), lastField.getText(), yearField.getText());
					}

					@Override
					protected void done() {
						try {
							message.setText(get());
						} catch (InterruptedException | ExecutionException e) {
							message.setText(e.getMessage());
						}
						btnSend.setEnabled(true);
					}
				}.execute();
			}
		});
		GridBagConstraints gbc_btnSend = new GridBagConstraints();
		gbc_btnSend.gridwidth = 2;
		gbc_btnSend.insets = new Insets(10, 10, 20, 10);
		gbc_btnSend.gridx = 0;
		gbc_btnSend.gridy = 4;
		getContentPane().add(btnSend, gbc_btnSend);

		pack();
	}

	private String toHex(byte[] bytes) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < bytes.length; i++)
			s.append(String.format("%02x", bytes[i]));
		return s.toString();
	}

	private String getResponse(String first, String last, String year) {
		try {
			byte[] bytesOfMessage = (first + last + year).getBytes("UTF-8");

			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] thedigest = md.digest(bytesOfMessage);
			String checksum = toHex(thedigest);

			Socket s = new Socket(proxy);
			s.connect(target, 20000);
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
			BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
			out.write("{\n");
			out.flush();
			Thread.sleep(100);
			out.write("\t\"birthyear\" : \"" + year + "\",\n");
			out.flush();
			Thread.sleep(100);
			out.write("\t\"checksum\" : \"" + checksum + "\",\n");
			out.flush();
			Thread.sleep(100);
			out.write("\t\"name\" : \"" + first + "\",\n");
			out.flush();
			Thread.sleep(100);
			out.write("\t\"surname\" : \"" + last + "\"\n");
			out.flush();
			Thread.sleep(100);
			out.write("}\n");
			out.flush();
			s.shutdownOutput();

			StringBuilder builder = new StringBuilder();
			builder.append(in.readLine());
			s.close();
			int quote = builder.lastIndexOf("\"");
			try {
				int age = Integer.parseInt(builder.substring(builder.lastIndexOf("\"", quote - 1) + 1, quote));
				if (age > 100) {
					return "What?! You're " + age + " years old?!";
				} else if (age < 50) {
					return "You're " + age + " years old";
				} else {
					return "Damn! You're " + age + " years old";
				}
			} catch (NumberFormatException e) {
				return e.getMessage();
			}
		} catch (IOException | NoSuchAlgorithmException | InterruptedException e) {
			return e.getMessage();
		}
	}

	public static void main(String[] args) {
		if (args == null || args.length != 2) {
			System.err.println("Usage: java -cp target/mallet-1.0-SNAPSHOT.jar demo.Client hostname port");
			System.exit(1);
		}
		InetSocketAddress target = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		String proxyHost = System.getProperty("socksProxyHost", null);
		String proxyPort = System.getProperty("socksProxyPort", null);
		Proxy proxy = Proxy.NO_PROXY;
		if (proxyHost != null)
			proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
		new Client(target, proxy).setVisible(true);
	}
}
