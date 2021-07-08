package mapati;


import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;





import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.util.Vector;

public class XMLRead 
{

	private Vector<String> output;
	private Logger logger = Logger.getLogger(XMLRead.class);

	public XMLRead()
	{
		output= new Vector<String>();
		PropertyConfigurator.configure(System.getProperty("user.dir")+"/config/log4j.properties");    			

	}
	
	public String[] execute(String input, String rootTag, String[] keyword)
	{

		String outputs[] = new String[keyword.length];
		
		try 
		{
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new ByteArrayInputStream(input.getBytes()));

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName(rootTag);

			for (int temp = 0; temp < nList.getLength(); temp++) 
			{	
				Node nNode = nList.item(temp);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) 
				{
					Element eElement = (Element) nNode;

					for(int i=0;i<keyword.length;i++)
					{	
						if(eElement.getElementsByTagName(keyword[i]).getLength()>0)
						{
							outputs[i]=eElement.getElementsByTagName(keyword[i]).item(0).getTextContent();
						}
						else
						{
							outputs[i]="";
						}
					}

				}

			}

		} 
		catch (Exception e) 
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
		return outputs;
	}
	
	public void execute(String input, String rootTag, Vector<String> keyword)
	{

		try 
		{

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new ByteArrayInputStream(input.getBytes()));

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName(rootTag);

			output.clear();

			for (int temp = 0; temp < nList.getLength(); temp++) 
			{	
				Node nNode = nList.item(temp);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) 
				{
					Element eElement = (Element) nNode;

					for(int i=0;i<keyword.size();i++)
					{	
						if(eElement.getElementsByTagName(keyword.get(i)).getLength()>0)
						{
							output.add(eElement.getElementsByTagName(keyword.get(i)).item(0).getTextContent());
						}
						else
						{
							output.add("");
						}
					}

				}

			}

		} 
		catch (Exception e) 
		{
			logger.error(this.getClass().getName()+" "+e.getMessage());
		}
	}

	
	public Vector<String> getOutput()
	{
		return output;
	}

}