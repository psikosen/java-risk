package javaRisk;
import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * The ServerProxy is the interface between the Risk game UI
 * and the network.
 * 
 * @author Dylan Hall
 * @author Trevor Mack
 */
public class ServerProxy implements UIListener {

	/**
	 * GUI.
	 */
	private RiskGUI gui;
	
	/**
	 * Associated ClientModel.
	 */
	private ClientModel model;
	
	/**
	 * List of players playing the current game.
	 */
	private List<Player> players = new ArrayList<Player>();
	
	/**
	 * Socket connected to server.
	 */
	private Socket socket;
	
	/**
	 * DataInputStream of the socket.
	 */
	private DataInputStream input;
	
	/**
	 * DataOutputStream of the socket.
	 */
	private DataOutputStream output;
	
	/**
	 * Used to determine if the gamename is already in progress on the server.
	 * null means not sure yet, true means the name is already in use.
	 */
	private Boolean status = null;
	
	/**
	 * Indicates which player's turn it is.
	 */
	private int turnIndicator;
	
	/**
	 * Flag for if the game has begun yet.
	 */
	private boolean gameStarted = false;
	
	/**
	 * Flag for who the winner is.
	 */
	private int winner = -1;
	
	/**
	 * Create a new ServerProxy based on the given socket.
	 * @param socket - the socket connected to the Risk server
	 */
	public ServerProxy(Socket socket) {
		this.socket = socket;
		try {
			this.input = new DataInputStream(socket.getInputStream());
			this.output = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Called when the user clicks on any territory.
	 * @param territory - the index of the clicked territory
	 */
	public void clicked(int territory) throws IOException {
		
		if (model.isMine(territory)) {
			gui.reset();
			model.setClicked(territory);
			gui.highlight(territory);
			
		} else {
			if (model.getClicked() != -1) 
			{
				// user has already clicked one of his own territories
				
				if (model.isAdjacent(model.getClicked(), territory)) {
					launchAttack(model.getClicked(), territory);
					model.setClicked(-1);
					gui.reset();
				}
			}
		}
	}
	
	/**
	 * User clicked "End Turn" button.
	 */
	public void endTurn() throws IOException {
		output.writeByte(Constants.END_TURN);
		gui.reset();
	}
	
	/**
	 * User launched an attack from one territory to another.
	 * @param src - the index of the attacking territory
	 * @param dest - the index of the defending territory
	 */
	public void launchAttack(int src, int dest) throws IOException {
		output.writeByte(Constants.ATTACK);
		output.writeInt(src);
		output.writeInt(dest);
	}
	
	/**
	 * User exited the program, causing a surrender.
	 */
	public void surrender() throws IOException {
		output.writeByte(Constants.SURRENDER);
		socket.close();
	}
	
	/**
	 * Received an indicator of a player's turn.
	 * @param player - the index of the player whose turn it is.
	 */
	public void turnIndicator(int player)
	{
		turnIndicator = player;
		gui.showPlayerTurn(player);
		gui.showGamePlayable(model.isMe(player));
	}
	
	/**
	 * Display an attack on the GUI.
	 * @param src - attacking territory index
	 * @param dest - defending territory index
	 * @param a_roll - attack roll
	 * @param d_roll - defense roll
	 */
	public void showAttack(int src, int dest, int a_roll, int d_roll)
	{
		gui.showAttack(src, dest);
		gui.showAttackRoll(a_roll);
		gui.showDefenseRoll(d_roll);
	}
	
	/**
	 * Update a territory. (Either game initialization or after attack)
	 * @param index - territory to update
	 * @param owner - new owner index of the territory
	 * @param size - size of the army on this territory
	 */
	public void update(int index, int owner, int size){
		model.updateTerritory(index, owner, size);
		gui.updateTerritory(index, model.getPlayerColor(owner), size);
		gui.showPlayerTurn(turnIndicator);
	}


	/**
	 * Used by the RiskClient to determine when to display the GUI.
	 * @return true if the server has sent the GAME_STARTED message
	 */
	public boolean gameIsStarted() {
		return gameStarted;
	}

	/**
	 * Request the Color representing a player by index.
	 * @return the given player's Color
	 */
	public Color requestPlayerColor(int player) {
		return model.getPlayerColor(player);
	}
	
	/**
	 * Set the associated GUI object.
	 * @param gui - the GUI to set
	 */
	public void setGUI(RiskGUI gui) {
		this.gui = gui;
	}
	
	/**
	 * Send the name of the game to join to the server.
	 * @param gameName - the name of the game to join
	 */
	public void joinGame(String gameName) throws IOException
	{
		output.writeByte(Constants.GAME_TO_JOIN);
		output.writeUTF(gameName);
		output.flush();
	}
	
	/**
	 * Send the name of the player joining a game. 
	 * @param playerName - the player's username
	 */
	public void playerInfo(String playerName) throws IOException
	{
		output.writeByte(Constants.PLAYER_INFO);
		output.writeUTF(playerName);
		output.flush();
	}
	
	/**
	 * Received information about a player from the server.
	 * @param player - the player's index
	 * @param color - the player's Color
	 * @param name - the player's Username
	 */
	public void receivedPlayerInfo(int player, Color color, String name) {
		players.add(new Player(player, name, color));
	}
	
	/**
	 * Signal to the server that this user is ready to play.
	 */
	public void ready() throws IOException
	{
		output.writeByte(Constants.READY);
		output.flush();
	}
	
	/**
	 * Set the associated model.
	 * @param model - the ClientModel to set.
	 */
	public void setModel(ClientModel model)
	{
		this.model = model;
	}
	
	/**
	 * Get a list of the names of players in the current game.
	 * @return array of player names
	 */
	public String[] getPlayerNames()
	{
		return model.getPlayerNames();
	}

	/**
	 * Reset the value of the "Game already started" status
	 */
	public void resetStatus()
	{
		status = null;
	}
	
	/**
	 * Get the value of the "Game already started" status
	 * @return
	 */
	public Boolean gameStatus() {
		return status;
	}
	
	/**
	 * Request from the model the number of territories a player has.
	 * @param player - the player index to check
	 */
	public int requestPlayerCount(int player) {
		return players.get(player).getNumTerritories();
	}
	
	/**
	 * Start this ServerProxy reading data from the server.
	 */
	public void start() {
		new Reader().start();
	}
	
	/**
	 * Private Reader class used to read data from the Socket
	 * connected to the server.
	 */
	private class Reader extends Thread {

		public void run() {
			try
			{
				while(socket.isConnected())
				{
					byte curr = input.readByte();
					
					switch(curr)
					{
					case Constants.GAME_STARTING:
						model.setPlayers(players);
						gameStarted = true;
						
						break;
					case Constants.TURN_IND:
						int player_num = input.readInt();
						turnIndicator(player_num);
						break;
						
					case Constants.ATTACK_MADE:
						int src = input.readInt();
						int dest = input.readInt();
						int a_roll = input.readInt();
						int d_roll = input.readInt();
						
						showAttack(src,dest, a_roll, d_roll);
						break;
						
					case Constants.TERRITORY_STATUS:
						int index = input.readInt();
						int owner = input.readInt();
						int size = input.readInt();
						
						update(index, owner, size);
						break;
						
					case Constants.PLAYER_INFO:
						int player = input.readInt();
						int rgb = input.readInt();
						String name = input.readUTF();
						receivedPlayerInfo(player, new Color(rgb), name);
						break;
						
					case Constants.WHO_AM_I:
						// Who Am I also used as the signal
						// that all player info has been sent
						int me = input.readInt();
						model.setPlayers(players);
						model.setMe(me);
						gui.setNames(model.getPlayerNames());
						break;
						
					case Constants.GAME_FINISHED:
						winner = input.readInt();
						if (model.isMe(winner))
						{
							gui.youWin();
						} else
						{
							gui.youLose();
						}
						gui.showGamePlayable(false);
						gui.showPlayerTurn(winner);
						// the winner gets >> << around his name
						socket.close();
						break;
						
					case Constants.GAME_ALREADY_STARTED:
						status = input.readBoolean();
						break;
						
					default:
						break;
					}

				}
			} catch (EOFException e) 
			{	
			} catch (IOException e)
			{
				if (winner == -1)
				{
					// only show a server error if nobody has won yet
					gui.showServerError();
				}
				try {socket.close(); } catch(IOException exc) {}
			}
		}
	}

}