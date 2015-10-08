package kr.ac.kaist.hybridroid.preanalysis.spots;

public class ArgumentHotspot implements Hotspot {
	private String methodName;
	private int paramNum;
	private int argIndex;
	
	public ArgumentHotspot(String methodName, int paramNum, int argIndex){
		this.methodName = methodName;
		this.paramNum = paramNum;
		this.argIndex = argIndex;
	}

	public String getMethodName() {
		return methodName;
	}

	public int getParamNum() {
		return paramNum;
	}

	public int getArgIndex() {
		return argIndex;
	}	
}
