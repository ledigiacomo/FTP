import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.*;
import java.util.Scanner;
import java.util.StringTokenizer;

public class FTPClient
{
	private static final int A = 65;
	private static final int Z = 90;
	private static final int a = 97;
	private static final int z = 122;
	private static final int ZERO = 48;
	private static final int NINE = 57;
	private static final String USER = "anonymous";
	private static final String PASS = "guest@";
	private static final int RETURN_CHAR = 13;
	private static final int NEWLINE = 10;

	private static boolean first  = true;
	private static String serverHost = "";
	private static String serverPort = ""; 
	private static int portNumber;
	private static int initialPortNumber;
	private static String pathName = "";
	private static Socket socket;
	private static DataOutputStream out;
	private static Scanner in;
	private static int retrNum = 1;

	public static void main(String[] args)
	{
		initialPortNumber = Integer.parseInt(args[0]);
		portNumber = initialPortNumber;
		Scanner keyboard = new Scanner(System.in);
		while(keyboard.hasNext())
		{
			String input = keyboard.nextLine();
			StringTokenizer tokenizer = new StringTokenizer(input);

			System.out.println(input);

			String cmd = tokenizer.nextToken();

			if(checkLine(input, cmd))
				try 
				{
					executeCommand(input, cmd);
				} catch (IOException e) { 
					e.printStackTrace();
				}
		}
	}

	private static boolean checkLine(String input, String cmd)
	{
		if(checkCommand(cmd) && checkParameter(input, cmd))
			return true;

		else
			return false;
	}

	private static boolean checkCommand(String cmd)
	{
		if(!(cmd.equalsIgnoreCase("Quit") || cmd.equalsIgnoreCase("Connect") || cmd.equalsIgnoreCase("GET")))
		{
			System.out.println("ERROR -- request");
			return false;
		}

		else 
			return true;
	}

	private static boolean checkParameter(String input, String cmd)
	{
		if(cmd.equalsIgnoreCase("Quit"))
		{
			if(cmd.length() != input.length())
			{
				System.out.println("ERROR -- request");
				return false;
			}

			else 
				return true;
		}

		else if(cmd.equalsIgnoreCase("GET"))
		{
			StringTokenizer tokenize = new StringTokenizer(input);
			tokenize.nextToken();
			String param = "";

			while(tokenize.hasMoreTokens())
				param += tokenize.nextToken();

			if(param.equals(""))
			{
				System.out.println("ERROR -- pathname");
				return false;
			}

			for(int i = 0; i < param.length(); i++)
			{
				if(param.charAt(i) > 128 || param.charAt(i) < 0)
				{
					System.out.println("ERROR -- pathname");
					return false;
				}
			}

			input = input.substring(3);
			for(int i = 0; i < input.length(); i++)
			{
				if(input.charAt(0) == 32)
					input = input.substring(1);

				else
					break;
			}

			pathName = input;

			return true;
		}

		//cmd == connect
		else
		{
			StringTokenizer tokenize = new StringTokenizer(input, " ");
			tokenize.nextToken();

			if(tokenize.hasMoreTokens())
				serverHost = tokenize.nextToken();

			else
			{
				System.out.println("ERROR -- request");
				return false;
			}

			if(tokenize.hasMoreTokens())
				serverPort = tokenize.nextToken();

			else
			{
				System.out.println("ERROR -- server-host");
				return false;
			}

			//there is more to the parameter than server-host and server-port
			if(tokenize.hasMoreTokens())
			{
				System.out.println("ERROR -- server-port");
				return false;
			}


			//test the bounds for first character to be in domain
			if(!((serverHost.charAt(0) >= A && serverHost.charAt(0) <= Z) || (serverHost.charAt(0) >= a && serverHost.charAt(0) <= z)))
			{
				System.out.println("ERROR -- server-host");
				return false;
			}

			//serverHost ends with a '.'
			if(serverHost.charAt(serverHost.length() - 1) == 46)
			{
				System.out.println("ERROR -- server-host");
				return false;
			}

			//checks to make sure serverHost has only valid chars
			for(int i = 1; i < serverHost.length(); i++)
			{
				if(!((serverHost.charAt(i) >= A && serverHost.charAt(i) <= Z) || (serverHost.charAt(i) >= a && serverHost.charAt(i) <= z) || 
						(serverHost.charAt(i) >= ZERO && serverHost.charAt(i) <= NINE) || serverHost.charAt(i) == 46))
				{
					System.out.println("ERROR -- server-host");
					return false;
				}

				if(serverHost.charAt(i) == 46 && (serverHost.charAt(i-1) == 46 || (serverHost.charAt(i+1) >= ZERO && serverHost.charAt(i+1) <= NINE)))
				{
					System.out.println("ERROR -- server-host");
					return false;
				}
			}

			try
			{
				Integer.parseInt(serverPort);
			}
			catch(NumberFormatException e)
			{
				System.out.println("ERROR -- server-port");
				return false;
			}

			if(Integer.parseInt(serverPort) < 0 || Integer.parseInt(serverPort) > 65535)
			{
				System.out.println("ERROR -- server-port");
				return false;
			}

			return true;
		}
	}

	private static boolean checkCode(String replyCode)
	{
		try
		{
			Integer.parseInt(replyCode);
		}
		catch(NumberFormatException e)
		{
			System.out.println("ERROR -- reply-code");
			return false;
		}

		if(!(Integer.parseInt(replyCode) >= 100 && Integer.parseInt(replyCode) <= 599))
		{
			System.out.println("ERROR -- reply-code");
			return false;
		}

		else 
			return true;
	}

	private static boolean checkText(String replyText, String replyCode)
	{
		if(replyText == "" && replyCode.equals("331"))
		{
			System.out.println("ERROR -- reply-text");        
			return false; 
		}

		for(int i = 0; i < replyText.length(); i++)
		{
			if(replyText.charAt(i) > 128)
			{  
				System.out.println("ERROR -- reply-text");        
				return false;          
			}
		}  

		return true;
	}

	private static String parseEndings(String input)
	{                
		for(int i = 0; i < input.length(); i++)
		{
			if(input.charAt(i) == RETURN_CHAR || input.charAt(i) == NEWLINE)
			{
				System.out.println(input.substring(0, i+1));
				System.out.println("ERROR -- <CRLF>");
				input = input.substring(i+1, input.length());
				i = 0;        
			}
		}

		return input;          
	}

	private static boolean parseServer() throws IOException
	{		

		String input = "";
		input = in.nextLine();
		StringTokenizer tokenizer = new StringTokenizer(input);

		input = parseEndings(input);

		String replyCode = tokenizer.nextToken();
		String replyText = "";
		while(tokenizer.hasMoreTokens())
			replyText += tokenizer.nextToken() + " ";

		if(checkCode(replyCode) && checkText(replyText, replyCode))
			System.out.println("FTP reply " + replyCode + " accepted. Text is: " + replyText);
		
		if(replyCode.charAt(0) == '5' || replyCode.charAt(0) == '4')
			return false;
		
		else 
			return true;
	}

	private static void executeCommand(String input, String cmd) throws IOException
	{	
		if(first)
		{
			if(!cmd.equalsIgnoreCase("Connect"))
				System.out.println("ERROR -- expecting CONNECT");

			else
			{
				first = false;
				executeCommand(input, cmd);
			}
		}

		else
		{
			if(cmd.equalsIgnoreCase("CONNECT"))
			{
				try 
				{
					if(socket != null && !socket.isClosed())
						socket.close();

					socket = new Socket(InetAddress.getByName(serverHost), Integer.parseInt(serverPort));
					out = new DataOutputStream(socket.getOutputStream());
					in = new Scanner(new InputStreamReader(socket.getInputStream()));

					System.out.println("CONNECT accepted for FTP server at host " + serverHost + " and port " + serverPort + '\r');
					parseServer();
					
					System.out.println("USER " + USER + "\r");
					out.writeBytes("USER " + USER + "\r\n");
					out.flush();
					parseServer();
					
					System.out.println("PASS " + PASS + "\r");
					out.writeBytes("PASS " + PASS + "\r\n");
					out.flush();
					parseServer();
					
					System.out.println("SYST\r");
					out.writeBytes("SYST\r\n");
					out.flush();
					parseServer();
					
					System.out.println("TYPE I\r");
					out.writeBytes("TYPE I\r\n");
					out.flush();
					parseServer();
					
					portNumber = initialPortNumber;
				} 
				catch (Exception e) { 
					System.out.println("CONNECT failed\r"); 
				} 
			}

			else if(cmd.equalsIgnoreCase("Quit"))
			{
				System.out.println("QUIT accepted, terminating FTP client");
				System.out.println("QUIT\r");
				out.writeBytes("QUIT\r\n");
				out.flush();
				parseServer();
				System.exit(0);
			}

			//cmd == get
			else
			{
				System.out.println("GET accepted for " + pathName + "\r");

				String myIP;
				InetAddress myInet = null;
				try 
				{
					myInet = InetAddress.getLocalHost();
				} catch (UnknownHostException e) { e.printStackTrace(); }
				myIP = myInet.getHostAddress();
				myIP = myIP.replace('.', ',');
				
				ServerSocket welcomeSoc = new ServerSocket(portNumber);

				System.out.println("PORT " + myIP + "," + portNumber/256 + "," + (portNumber - ((portNumber/256) * 256)) + "\r");
				out.writeBytes("PORT " + myIP + "," + portNumber/256 + "," + (portNumber - ((portNumber/256) * 256)) + "\r\n");
				out.flush();	
				parseServer();

				System.out.println("RETR " + pathName + "\r");
				out.writeBytes("RETR " + pathName + "\r\n");
				out.flush();
				portNumber++;	
				
				if(parseServer())
				{
					Socket dataSoc = welcomeSoc.accept();
					byte[] inFile = new byte[999999];
					
					DataInputStream dataIn = new DataInputStream(dataSoc.getInputStream());
					try {dataIn.readFully(inFile);}
					catch(EOFException e){//do nothing
					}
					Path outFile = FileSystems.getDefault().getPath("retr_files/file" + retrNum);
					Files.deleteIfExists(outFile);
					FileOutputStream createFile = new FileOutputStream(outFile.toString());
					createFile.write(inFile);
					createFile.close();
	
					retrNum++;
					dataSoc.close();
					parseServer();
				}
			}
		}
	}
}
