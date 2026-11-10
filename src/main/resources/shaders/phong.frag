#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;
uniform sampler2D textureSampler;
uniform sampler2D shadowMap;
uniform bool useTexture;
uniform bool useBlinnPhong;
uniform bool useShadow;
uniform float biasSlope;
uniform float biasMin;

float ShadowCalculation(vec4 fragPosLS, vec3 norm, vec3 lightDir) {
    vec3 p = fragPosLS.xyz / fragPosLS.w;
    p = p * 0.5 + 0.5;
    if (p.z > 1.0) return 0.0;
    float bias = max(biasSlope * (1.0 - dot(norm, lightDir)), biasMin);
    float shadow = 0.0;
    vec2 texel = 1.0 / textureSize(shadowMap, 0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float closest = texture(shadowMap, p.xy + vec2(x, y) * texel).r;
            shadow += (p.z - bias > closest) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

void main() {
    // 1. Ambient
    float ambientStrength = 0.2;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Diffuse (Lambertian)
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Specular (Phong vs Blinn-Phong)
    float specularStrength = 0.8;
    vec3 viewDir = normalize(viewPos - FragPos);
    float spec = 0.0;
    if (useBlinnPhong) {
        vec3 halfwayDir = normalize(lightDir + viewDir);
        spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0);
    } else {
        vec3 reflectDir = reflect(-lightDir, norm);
        spec = pow(max(dot(viewDir, reflectDir), 0.0), 16.0);
    }
    vec3 specular = specularStrength * spec * lightColor;

    // 4. Shadow (faktor 0.2 jika di dalam bayangan)
    float inShadow = useShadow ? ShadowCalculation(FragPosLightSpace, norm, lightDir) : 0.0;
    float shadowFactor = mix(1.0, 0.2, inShadow);

    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb : objectColor;
    vec3 result = (ambient + shadowFactor * (diffuse + specular)) * baseColor;
    FragColor = vec4(result, 1.0);
}