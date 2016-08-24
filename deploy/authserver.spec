%define name            authserver
%define release         1

%define appuser	        %{name}
%define appgroup        %{name}
%define admingroup      %{name}adm

%define download        authz-page-%{version}.jar
# %define SOURCE0_URL   https://waterfall.auckland.ac.nz/nexus/content/repositories/releases/nz/ac/auckland/findathesis/findathesis-war/%{version}/%{download}
%define SOURCE0_URL     https://nexus.auckland.ac.nz/nexus/content/repositories/releases/nz/ac/auckland/auth/authz-page/%{version}/%{download}

Summary:        OAuth2 Authorization Server
Name:           %{name}
Version:        %{version}
Release:        %{release}
Epoch:          0
Group:          System/Daemons
License:        Proprietary
BuildArch:      noarch
Source0:        %{download}
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}
Requires:       java-1.8.0

%description
OAuth2 Authorization Server

%prep
# Download the sources
if [ ! -s %{SOURCE0} ]; then
	if [ -s "%{SOURCE0_LOCAL}" ]; then
		cp %{SOURCE0_LOCAL} %{SOURCE0}
	elif [ %{SOURCE0_URL} ]; then
		wget -nv -t 1 -T 3 -O %{SOURCE0}.tmp %{SOURCE0_URL}
		mv %{SOURCE0}.tmp %{SOURCE0}
	fi
fi

# Extract any files we'll need to deploy directly later
%setup -c -T
unzip %{SOURCE0} application.properties -d AppProps
unzip %{SOURCE0} %{name}.service %{name}.initd -d AppScripts

%install
rm -rf %{buildroot}

# Create directories
mkdir -p %{buildroot}/opt/%{name}
mkdir -p %{buildroot}/usr/lib/systemd/system
mkdir -p %{buildroot}/etc/%{name}
mkdir -p %{buildroot}/var/lib/%{name}/tmp
mkdir -p %{buildroot}/var/log/%{name}
mkdir -p %{buildroot}/usr/share
mkdir -p %{buildroot}/var/run/%{name}

# Copy files
cp %{SOURCE0} %{buildroot}/usr/share/%{name}.jar

# Add the run (former initd) script
[ -d AppScripts ] && cp -rp AppScripts/%{name}.initd %{buildroot}/opt/%{name}/%{name}
#ln -s /etc/app-initd/init.d.script %{buildroot}/etc/init.d/%{name}

#Add the systemd script
[ -d AppScripts ] && cp -rp AppScripts/%{name}.service %{buildroot}/usr/lib/systemd/system/%{name}.service

# Add the configuration templates
[ -d AppProps ] && cp -rp AppProps/* %{buildroot}/etc/%{name}

%pre
# Create user if does not exist
groupadd -f %{appgroup}
groupadd -f %{admingroup}
id %{appuser} &>/dev/null || useradd -r -d /etc/%{name} -g %{appgroup} %{appuser}

%files
%defattr(0644,root,root,0755)
/usr/share/%{name}.jar


%defattr(0755,root,root,0755)
#/etc/init.d/%{name}
/opt/%{name}/%{name}

%defattr(0664,root,root,0664)
/usr/lib/systemd/system/%{name}.service

%defattr(0664,root,%{admingroup},2775)
%config(noreplace) /etc/%{name}

%defattr(0664,%{appuser},%{admingroup},2775)
/var/lib/%{name}

%defattr(0644,%{appuser},root,0755)
/var/log/%{name}
/var/run/%{name}

%post
systemctl daemon-reload
if [ $1 -eq 2 ] ; then
  # Package upgrade
  # /sbin/service authserver restart
fi

