import groovy.json.JsonSlurper;

def getAllSailMembers(String access_token) {
  def membersRequest = "https://api.github.com/teams/1591956/members?access_token="+access_token;
  def response = membersRequest.toURL().getText();
  def parsed = new JsonSlurper().parseText(response);
  def members = new String[parsed.items.size()];
  for (int k = 0; k < parsed.items.size(); k++) {
    def member = parsed[k];
    def name = member.login;
    members[k] = name;
  }
  return members;
}

def getIndividualIssues(String access_token, String member, List prs_list) {
  def memberRequest = "https://api.github.com/repos/appian/ae/issues?access_token="+access_token+"&creator="+member+"&state=closed&since=2018-10-01";
  def response = memberRequest.toURL().getText();
  def parsed = new JsonSlurper().parseText(response);
  for (int k = 0; k < parsed.items.size(); k++) {
    def issue = parsed[k];
    def url = issue.url;
    prs_list.add(url);
  }
  return prs_list;
}

def getPRs(String access_token) {
  def members = getAllSailMembers(access_token);
  println members;
  def prs_list = [];
  for (String member : members) {
    getIndividualIssues(access_token, member, prs_list);
  }
  return prs_list;
}

def getCommentStats(String access_token, String comments_url) {
  def comments_request = comments_url+"?access_token="+access_token;
  def response = comments_request.toURL().getText();
  def parsed = new JsonSlurper().parseText(response);
  for (int k = 0; k < parsed.items.size(); k++) {
    def comment = parsed.body;
    // check for code review, po review, qe review, and merge
  }
}

def getIndividualStats(String access_token, String issueUrl) {
  def issueRequest = issueUrl+"?access_token="+access_token;
  def response = issueRequest.toURL().getText();
  def parsed = new JsonSlurper().parseText(response);
  def comments_url = parsed.comments_url;
  getCommentStats(access_token, comments_url);
}

def getAllStats(String access_token) {
  def all_prs = getPRs(access_token);
  for (String pr_url : all_prs) {
    getIndividualStats(access_token, pr_url);
  }
}

getAllStats(args[0])
