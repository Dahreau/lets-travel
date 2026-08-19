FROM jenkins/jenkins:lts-jdk21

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER root
RUN mkdir -p /var/jenkins_home/.m2 && chown -R jenkins:jenkins /var/jenkins_home/.m2

# docker-ce-cli + docker-compose-plugin (client + plugin "compose" seuls, pas
# docker.io/containerd) pour que le stage Deploy pilote le démon Docker de
# l'hôte via le socket monté (voir infra/ci/docker-compose.yml). Ni docker.io
# ni son paquet dérivé docker-cli ne fournissent le plugin compose : il n'existe
# que dans le dépôt officiel Docker, absent des dépôts Debian standards. Codename
# et architecture détectés dynamiquement pour rester valides si l'image de base
# Jenkins ou la machine hôte changent.
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
  && mkdir -p /etc/apt/keyrings \
  && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
  && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends nodejs docker-ce-cli docker-compose-plugin python3-pip sudo \
  && rm -rf /var/lib/apt/lists/* \
  && pip install --break-system-packages ansible \
  && ansible-galaxy collection install community.docker

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/var/jenkins_home/casc.yaml"
