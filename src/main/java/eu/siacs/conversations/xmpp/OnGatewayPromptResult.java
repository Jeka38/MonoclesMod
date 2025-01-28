package eu.siacs.conversations.xmpp;

public interface OnGatewayPromptResult {
    // if prompt is null, there was an error
    // errorText may or may not be set
    public void onGatewayPromptResult(String prompt, String errorText);
}
