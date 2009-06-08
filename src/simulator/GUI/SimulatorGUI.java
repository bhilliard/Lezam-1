package simulator.GUI;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import simulator.BasicSimulator;
import simulator.Reports;
import simulator.SimAgent;
import simulator.parser.GameStatus;

public class SimulatorGUI extends JFrame {

	private File _fileToParse;
	private GameStatus _gameStatus;
	private SetupPanel startPanel;
	private String _agentOut;
	private String _agentIn;
	private int _numSims;
	private Dimension prefSize;
	private BasicSimulator _simulator;

	public SimulatorGUI() {
		super("TAC AA Simulator");
		prefSize = new Dimension(800,600);
		this.setSize(prefSize);
		this.setPreferredSize(prefSize);
		this.setLayout(new FlowLayout());
		Container contentPane = this.getContentPane();
		startPanel = new SetupPanel(this);
		contentPane.add(startPanel);
		this.pack();
		this.setVisible(true);
	}
	
	public void setGameToParse(File file) {
		_fileToParse = file;
		SetupPanel2 parsePanel = new SetupPanel2(this,file);
		switchFrames(parsePanel);
	}
	
	public void setGameStatus(GameStatus status) {
		_gameStatus = status;
		SetupPanel3 simPanel = new SetupPanel3(this, status);
		switchFrames(simPanel);
	}
	
	public void setNumSims(BasicSimulator simulator, String agentIn, String agentOut, int numSims) throws IOException, ParseException {
		_simulator = simulator;
		_agentIn = agentIn;
		_agentOut = agentOut;
		_numSims = numSims;
		String[] advertisers = _gameStatus.getAdvertisers();
		int advIdx = 0;
		for(int i = 0; i < advertisers.length; i++) {
			if(agentOut.equals(advertisers[i])) {
				advIdx = i;
			}
		}

		HashMap<String, LinkedList<LinkedList<Reports>>> allReportsLists = new HashMap<String, LinkedList<LinkedList<Reports>>>();
		
		for(int i = 0; i < advertisers.length; i++) {
			/*
			 * First index is for simulation #, second index is for day
			 */
			LinkedList<LinkedList<Reports>> reportsLists = new LinkedList<LinkedList<Reports>>();
			for(int j = 0; j < _numSims; j++) {
				LinkedList<Reports> reportsList = new LinkedList<Reports>();
				reportsLists.add(reportsList);
			}
				allReportsLists.put(advertisers[i], reportsLists);
		}
		
		for(int i = 0; i < _numSims; i++) {
			for(int day = 2; day <= 59; day++) {
				_simulator.initializeGameState(_gameStatus, day, advIdx);
				ArrayList<SimAgent> agents = _simulator.runSimulation(agentIn);
				for(SimAgent agent : agents) {
					String agentID = agent.getAdvId();
					LinkedList<LinkedList<Reports>>	reportsLists = allReportsLists.get(agentID);
					LinkedList<Reports> reportsList = reportsLists.get(i);
					reportsList.add(new Reports(agent.buildQueryReport(),agent.buildSalesReport()));
				}
			}
		}
		
		MainPanel mainPanel = new MainPanel(this,_simulator,_gameStatus,_agentIn,_agentOut,_numSims, prefSize,allReportsLists);
		switchFrames(mainPanel);
	}

	public void resetFile() {
		switchFrames(startPanel);
	}

	public void switchFrames(Component comp) {
		this.setVisible(false);
		Container contentPane = this.getContentPane();
		contentPane.removeAll();
		contentPane.add(comp);
		this.pack();
		this.setVisible(true);
	}

	public static void main(String[] args) {
		JFrame f = new SimulatorGUI();
	}
}