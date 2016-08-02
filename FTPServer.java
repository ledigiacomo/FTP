import java.net.*;
import java.util.StringTokenizer;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class FTPServer
{
	private static final int RETURN_CHAR = 13;
	private static final int NEWLINE = 10;
	private static int[] hostport = new int[6];
	private static String pathname;
	private static boolean used = false;
	private static boolean passed = false;
	private static boolean portOpen = false;
	private static String port;
	private static DataOutputStream out;
	private static Scanner in;
	private static Socket acceptSoc;

	public static void main(String[] args) throws IOException
	{
		ServerSocket servSoc = new ServerSocket(Integer.parseInt(args[0]));
		while(true)
		{
			used = false;
			passed = false;
			portOpen = false;

			acceptSoc = servSoc.accept();
			out = new DataOutputStream(acceptSoc.getOutputStream());
	
			System.out.println("220 COMP 431 FTP server ready.\r");
			out.writeBytes("220 COMP 431 FTP server ready.\r\n");
			out.flush();
	
			in = new Scanner(new InputStreamReader(acceptSoc.getInputStream()));
	
			String input = "";      
	
			//tests USER and PASS are first
			while(!acceptSoc.isClosed())
			{			
				input = in.nextLine();
			
				StringTokenizer tokenize = new StringTokenizer(input, " ");
				String command = "";
				String parameter = "";  
	
				if(tokenize.hasMoreTokens())
					command = tokenize.nextToken();
	
				for(int i = 0; i < tokenize.countTokens(); i++) 
					parameter += tokenize.nextToken(); 
	
				System.out.println(input + "\r");
	
				if(checkLine(input, command, parameter))
				{
					if(command.equalsIgnoreCase("USER") || command.equalsIgnoreCase("QUIT"))
					{
						executeCommand(input, command, parameter);
						break;
					}
	
					else
					{
						System.out.println("530 Not logged in.\r");
						out.writeBytes("530 Not logged in.\r\n");
						out.flush();
					}
						
				}         
			}
	
			//main loop
			while(!acceptSoc.isClosed())
			{   
				if(in.hasNextLine())
					input = in.nextLine();

				else 
					break;
				
				input = parseEndings(input);
	
				StringTokenizer tokenizer = new StringTokenizer(input, " ");
				String cmd = "";
				String param = "";
	
				if(tokenizer.hasMoreTokens())
					cmd = tokenizer.nextToken();
	
				for(int i = 0; i < tokenizer.countTokens(); i++)
					param += tokenizer.nextToken(); 
	
				System.out.println(input + "\r");
	
				if(checkLine(input, cmd, param))
					executeCommand(input, cmd, param);
			}
		}
	}

	//makes sure there are no parameter, command or crlf errors in an inputed line
	private static boolean checkLine(String input, String cmd, String param) throws IOException
	{
		if(checkCommand(input, cmd) && checkParameter(input, cmd, param) && checkCRLF(cmd, param))
			return true;    
		else 
			return false;    
	}

	//checks for commands that end in \r or \n 
	//returns: the next command that ends in \r\n
	private static String parseEndings(String input) throws IOException
	{                
		for(int i = 0; i < input.length(); i++)
		{
			if(input.charAt(i) == RETURN_CHAR || input.charAt(i) == NEWLINE)
			{
				System.out.println(input.substring(0, i+1));
				System.out.println("501 Syntax error in parameter.\r");
				out.writeBytes("501 Syntax error in parameter.\r\n");
				out.flush();
				input = input.substring(i+1, input.length());
				i = 0;        
			}
		}

		return input;          
	}

	private static boolean checkCommand(String input, String command) throws IOException
	{                
		if(command.equalsIgnoreCase("QUIT"))
			return true;                    

		//sees if it is a valid command or if the command begins with whitespace
		if(!command.equalsIgnoreCase("USER") && !command.equalsIgnoreCase("PASS") && !command.equalsIgnoreCase("TYPE") 
				&& !command.equalsIgnoreCase("SYST") && !command.equalsIgnoreCase("NOOP") && !command.equalsIgnoreCase("QUIT")
				&& !command.equalsIgnoreCase("PORT") && !command.equalsIgnoreCase("RETR") || input.charAt(0) == 32)
		{
			System.out.println("500 Syntax error, command unrecognized.\r");
			out.writeBytes("500 Syntax error, command unrecognized.\r\n");
			out.flush();

			return false;                
		}

		//makes sure a retr is preceeded by a port
		else if(command.equalsIgnoreCase("RETR"))
		{
			if(!portOpen)
			{
				System.out.println("503 Bad sequence of commands.\r");
				out.writeBytes("503 Bad sequence of commands.\r\n");
				out.flush();

				return false;             
			}
			else
				return true;
		}

		//makes sure a pass is preceeded by a user
		else if(command.equalsIgnoreCase("PASS"))
		{
			if(!used)
			{
				System.out.println("503 Bad sequence of commands.\r");
				out.writeBytes("503 Bad sequence of commands.\r\n");
				out.flush();

				return false;
			}

			else
				return true;
		}                         

		//makes sure user has not already been used
		else if(command.equalsIgnoreCase("USER"))
		{                      
			if(used)
			{
				System.out.println("503 Bad sequence of commands.\r");
				out.writeBytes("503 Bad sequence of commands.\r\n");
				out.flush();

				return false;
			}

			else
				return true;
		}

		else if(passed && used)
			return true;
		else 
		{
			System.out.println("503 Bad sequence of commands.\r");
			out.writeBytes("503 Bad sequence of commands.\r\n"); 
			out.flush();

			return false;                
		}
	}       

	private static boolean checkParameter(String input, String cmd, String param) throws IOException
	{                
		//check param if cmd is USER or PASS
		if(cmd.equalsIgnoreCase("USER") || cmd.equalsIgnoreCase("PASS") || cmd.equalsIgnoreCase("RETR"))
		{        
			//if param is the empty string, determine if it is a command or param error
			if(param == "")
			{
				//if there is a whitespace
				if(cmd.length() < input.length())
				{
					System.out.println("501 Syntax error in parameter.\r");
					out.writeBytes("501 Syntax error in parameter.\r\n");
					out.flush();

				}

				else 
				{
					System.out.println("500 Syntax error, command unrecognized.\r\n");
					out.writeBytes("500 Syntax error, command unrecognized.\r\n");
					out.flush();

				}

				return false;
			}

			//checks param for non-ASCII values
			else        
			{         
				for(int i = 0; i < param.length(); i++)
				{
					if(param.charAt(i) > 128)
					{  
						System.out.println("501 Syntax error in parameter.\r");
						out.writeBytes("501 Syntax error in parameter.\r\n");   
						out.flush();

						return false;          
					}
				}        
			}                               
		}                                       

		//checks param for proper type iff cmd is type
		else if(cmd.equalsIgnoreCase("TYPE") && (!(param.equals("A") || param.equals("I"))))
		{
			System.out.println("501 Syntax error in parameter.\r");
			out.writeBytes("501 Syntax error in parameter.\r\n");
			out.flush();

			return false;                
		}

		//checks host-port
		else if(cmd.equalsIgnoreCase("PORT"))
		{        
			//checks for whitespace in the port parameter
			String paramStr = input.substring(cmd.length() + 1);
			if(paramStr.length() != param.length())
			{
				System.out.println("501 Syntax error in parameter.\r");
				out.writeBytes("501 Syntax error in parameter.\r");
				out.flush();

				return false;
			}

			int idx = 0;
			String str = "";
			for(int i = 0; i < param.length(); i++)           
			{
				//if a comma is read, check that the preceding chars are valid numbers
				if(param.charAt(i) == 44)
				{
					for(int j = 0; j < str.length(); j++)
						if(str.charAt(j) > 57 || str.charAt(j) < 48)
						{
							System.out.println("501 Syntax error in parameter.\r");
							out.writeBytes("501 Syntax error in parameter.\r\n");
							out.flush();

							return false;
						}                  

					hostport[idx] = Integer.parseInt(str);

					//makes sure the number is [0,255]
					if(hostport[idx] < 0 || hostport[idx] > 255)
					{         
						System.out.println("501 Syntax error in parameter.\r"); 
						out.writeBytes("501 Syntax error in parameter.\r\n"); 
						out.flush();

						return false;              
					}        

					idx++;                  
					str = "";       
				}

				else
					str+=param.charAt(i);

				//makes sure there arent too many port statements
				if(idx > 5)     
				{
					System.out.println("501 Syntax error in parameter.\r");
					out.writeBytes("501 Syntax error in parameter.\r\n");
					out.flush();

					return false;
				}
			}

			//checks the last port statement       
			//it is not caught in the earlier for loop because it does not have a comma after it
			for(int j = 0; j < str.length(); j++)
				if(str.charAt(j) > 57 || str.charAt(j) < 48)
				{
					System.out.println("501 Syntax error in parameter.\r");
					out.writeBytes("501 Syntax error in parameter.\r\n");
					out.flush();

					return false;
				}

			hostport[idx] = Integer.parseInt(str);

			if(hostport[idx] < 0 || hostport[idx] > 255)
			{      
				System.out.println("501 Syntax error in parameter.\r");
				out.writeBytes("501 Syntax error in parameter.\r\n");
				out.flush();

				return false;
			}

			//makes sure there are an appropriate number of port numbers
			if(idx != 5)                               
			{    
				System.out.println("501 Syntax error in parameter.\r");
				out.writeBytes("501 Syntax error in parameter.\r\n");
				out.flush();

				return false;   
			}
		}

		return true;
	}

	//checks for a CRLF error iff cmd is SYST, QUIT, NOOP
	private static boolean checkCRLF(String cmd, String param) throws IOException
	{
		if((cmd.equalsIgnoreCase("SYST") || cmd.equalsIgnoreCase("QUIT") || cmd.equalsIgnoreCase("NOOP")) && param.equals(" "))
		{
			System.out.println("501 Syntax error in parameter.\r");
			out.writeBytes("501 Syntax error in parameter.\r\n");
			out.flush();

			return false;
		}

		else 
			return true;
	}

	private static void executeCommand(String input, String cmd, String param) throws IOException        
	{
		//prints prompt and exits program iff cmd is quit
		if(cmd.equalsIgnoreCase("QUIT"))
		{ 
			System.out.println("200 Command OK.\r");
			out.writeBytes("221 Goodbye.\r\n");
			out.flush();
			acceptSoc.close();
			used = false;
			passed = false;
			portOpen = false;
		}               

		//handles noop cmd
		else if(cmd.equalsIgnoreCase("NOOP"))
		{          
			System.out.println("200 Command OK.\r");
			out.writeBytes("200 Command OK.\r\n");
			out.flush();
		}     

		//handles type cmd
		else if(cmd.equalsIgnoreCase("TYPE"))
		{
			if(param.equals("A"))
			{
				System.out.println("200 Type set to A.\r");
				out.writeBytes("200 Type set to A.\r\n");
				out.flush();
			}
			
			else
			{
				System.out.println("200 Type set to I.\r");
				out.writeBytes("200 Type set to I.\r\n");
				out.flush();

			}
		}

		//handles SYST cmd
		else if(cmd.equalsIgnoreCase("SYST"))
		{
			System.out.println("215 UNIX Type: L8.\r");
			out.writeBytes("215 UNIX Type: L8.\r\n");
			out.flush();

		}

		else if(cmd.equalsIgnoreCase("PASS"))   
		{
			System.out.println("230 Guest login OK.\r");
			out.writeBytes("230 Guest login OK.\r\n");
			out.flush();
			passed = true;
		}        

		else if(cmd.equalsIgnoreCase("USER"))
		{
			System.out.println("331 Guest access OK, send password.\r"); 
			out.writeBytes("331 Guest access OK, send password.\r\n"); 
			out.flush();
			used = true;
		}

		else if(cmd.equalsIgnoreCase("RETR"))
		{
			pathname = param;

			//removes / or \ from beginning of file name
			while(pathname.charAt(0) == 47 || pathname.charAt(0) == 92)
				pathname=pathname.substring(1);

			Path inFile = FileSystems.getDefault().getPath(pathname);

			if(!Files.exists(inFile))
			{
				System.out.println("550 File not found or access denied.\r");
				out.writeBytes("550 File not found or access denied.\r\n");
				out.flush();
			}
			else
			{
				System.out.println("150 File status okay.\r");
				out.writeBytes("150 File status okay.\r\n");
				out.flush();
				
				int port = hostport[4]*256+hostport[5];
				Socket dataSoc = null;
				try
				{
					dataSoc = new Socket(acceptSoc.getInetAddress(), port);
				} catch (Exception e) 
				{
					System.out.println("425 Can not open data Connection\r");
					out.writeBytes("425 Can not open data Connection\r\n");
					out.flush();
				}
				
				if(dataSoc != null)
				{
					DataOutputStream outData = new DataOutputStream(dataSoc.getOutputStream());
					
					try 
					{
						byte[] data = Files.readAllBytes(inFile);
						outData.write(data, 0, data.length);
						outData.flush();
					} catch (IOException e) {
						e.printStackTrace();
					}
	
					if(!portOpen)
					{
						System.out.println("503 Bad sequence of commands.\r");
						out.writeBytes("503 Bad sequence of commands.\r\n");
						out.flush();
						dataSoc.close();
						return;
					}
	
					else
					{
						System.out.println("250 Requested file action completed.\r");
						out.writeBytes("250 Requested file action completed.\r\n");
						out.flush();
						//retrNum++;              
						portOpen = false;
						dataSoc.close();
					}
				}
			}
		}        


		//parses and calculates a port command
		else if(cmd.equalsIgnoreCase("PORT"))
		{
			portOpen = true;
			port = "";

			for(int i = 0; i < 3; i++)
				port+=hostport[i] + ".";
			port+=hostport[3] + ",";

			port+=hostport[4]*256+hostport[5];
					
			System.out.println("200 Port command successful (" + port + ").\r");
			out.writeBytes("200 Port command successful (" + port + ").\r\n");
			out.flush();
		}
	}
}

