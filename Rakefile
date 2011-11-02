task :prepare_versions do
  fail('VERSION must be provided - eg, VERSION=0.9.3') unless ENV['VERSION']
  
  @version = ENV['VERSION']
  @tag_name = "accent_#{@version.gsub(/\./, "_")}"
end

task :prepare_dev_version do
  @dev_version = ENV['DEV_VERSION']
  unless @dev_version
    require 'rexml/document'

    pom = REXML::Document.new(File.read('pom.xml'))
    @dev_version = pom.elements["project/version"].text
  end
end

task :prepare => [:prepare_versions, :prepare_dev_version] do
  puts "Preparing release #{@version} with tag #{@tag_name}, next dev version will be #{@dev_version}"
  sh "mvn release:clean release:prepare -B -DpushChanges=true -DpreparationGoals=validate -DreleaseVersion=#{@version} -DdevelopmentVersion=#{@dev_version} -Dtag=#{@tag_name}"
end

task :release => :prepare do
  puts "Performing release #{@version}"
  sh %Q{mvn -Dgpg.passphrase="xxxxxxx" -Darguments="-Dgpg.passphrase=xxxxxxxxx" release:perform}
end
